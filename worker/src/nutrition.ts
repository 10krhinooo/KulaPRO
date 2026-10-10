import Anthropic from '@anthropic-ai/sdk';
import { zodOutputFormat } from '@anthropic-ai/sdk/helpers/zod';
import { z } from 'zod';

/**
 * What the model must return, and nothing else.
 *
 * Every optional-looking field here is load bearing. [assumed_portion] and [confidence] are
 * what let the app present a number as the estimate it is: portion size cannot be measured
 * from one photograph, so a result that hid its own basis would be a confident lie.
 */
export const DishNutrition = z.object({
  is_food: z.boolean(),
  dish_name: z.string(),
  cuisine: z.string().nullable(),
  confidence: z.enum(['high', 'medium', 'low']),
  assumed_portion: z.string(),
  calories_kcal: z.number(),
  protein_g: z.number(),
  carbs_g: z.number(),
  fat_g: z.number(),
  fibre_g: z.number(),
  likely_ingredients: z.array(z.string()),
  health_notes: z.array(z.string()),
});

export type DishNutrition = z.infer<typeof DishNutrition>;

/**
 * Kept as a stable prefix so it can be cached between calls, which is most of the input
 * cost of a scan.
 *
 * The allergen instruction is not a style preference. An image classifier is not an
 * allergen test, and a person with a real allergy acting on one could be badly hurt, so the
 * model is told to route that question to the restaurant rather than answer it.
 */
export const SYSTEM_PROMPT = `You estimate the nutrition of a dish from a single photograph, for a restaurant booking app.

Report what you can actually see. A single photograph does not show portion weight, oil, butter, sugar in a sauce, or anything under the surface, so every number you give is an estimate against a stated assumption rather than a measurement.

Rules:
- State the serving you calculated against in assumed_portion, in plain words a diner would use, for example "one restaurant main, about 350g".
- Set confidence to low when the dish is ambiguous, partly hidden, or could be prepared very differently in different kitchens. Prefer low over a confident guess.
- Never make an allergen claim and never say whether a dish is safe for anyone. If the photograph raises an allergen question, put "Ask the restaurant about allergens" in health_notes and nothing more specific.
- Never give medical or dietary advice. health_notes describes the dish, for example "high in saturated fat", not what the reader should do.
- If the image is not food, set is_food to false, leave dish_name empty, and set every number to 0. Do not invent macros for a photograph of something else.`;

export interface ScanRequest {
  imageBase64: string;
  mediaType: 'image/jpeg' | 'image/png' | 'image/webp';
  model: string;
  apiKey: string;
}

/**
 * Asks the model to read the plate.
 *
 * Structured output rather than prose, so the Android client deserialises a fixed contract.
 * A parse failure returns null rather than throwing, because the caller has a better answer
 * for the user than a stack trace.
 */
export async function estimateNutrition(
  request: ScanRequest,
): Promise<DishNutrition | null> {
  const client = new Anthropic({ apiKey: request.apiKey });

  const response = await client.messages.parse({
    model: request.model,
    max_tokens: 2000,
    system: [
      {
        type: 'text',
        text: SYSTEM_PROMPT,
        // The prompt is identical on every scan, so paying full price for it every time is
        // waste. This is the single largest saving available on a cache miss.
        cache_control: { type: 'ephemeral' },
      },
    ],
    messages: [
      {
        role: 'user',
        content: [
          {
            type: 'image',
            source: {
              type: 'base64',
              media_type: request.mediaType,
              data: request.imageBase64,
            },
          },
          { type: 'text', text: 'Identify this dish and estimate its nutrition.' },
        ],
      },
    ],
    output_config: { format: zodOutputFormat(DishNutrition) },
  });

  return response.parsed_output ?? null;
}

/**
 * A result for something that is not food.
 *
 * Built here rather than trusted from the model, so a non-food photograph can never come
 * back carrying macros somebody invented.
 */
export function notFood(): DishNutrition {
  return {
    is_food: false,
    dish_name: '',
    cuisine: null,
    confidence: 'high',
    assumed_portion: '',
    calories_kcal: 0,
    protein_g: 0,
    carbs_g: 0,
    fat_g: 0,
    fibre_g: 0,
    likely_ingredients: [],
    health_notes: [],
  };
}

/** Strips macros off anything the model did not identify as food. */
export function sanitise(result: DishNutrition): DishNutrition {
  return result.is_food ? result : notFood();
}
