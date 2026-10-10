import { describe, expect, it } from 'vitest';
import { DishNutrition, SYSTEM_PROMPT, notFood, sanitise } from '../src/nutrition';

describe('the result contract', () => {
  it('accepts a complete result', () => {
    const parsed = DishNutrition.safeParse({
      is_food: true,
      dish_name: 'Ugali na nyama',
      cuisine: 'Kenyan',
      confidence: 'medium',
      assumed_portion: 'one restaurant main, about 400g',
      calories_kcal: 780,
      protein_g: 42,
      carbs_g: 88,
      fat_g: 28,
      fibre_g: 6,
      likely_ingredients: ['maize flour', 'beef'],
      health_notes: ['Ask the restaurant about allergens'],
    });

    expect(parsed.success).toBe(true);
  });

  it('refuses a result that omits its own confidence', () => {
    const parsed = DishNutrition.safeParse({ ...notFood(), confidence: undefined });

    expect(parsed.success).toBe(false);
  });

  it('refuses a confidence the app has no wording for', () => {
    const parsed = DishNutrition.safeParse({ ...notFood(), confidence: 'certain' });

    expect(parsed.success).toBe(false);
  });

  it('allows a dish whose cuisine cannot be placed', () => {
    const parsed = DishNutrition.safeParse({ ...notFood(), cuisine: null });

    expect(parsed.success).toBe(true);
  });
});

describe('sanitise', () => {
  it('leaves a real dish alone', () => {
    const dish = { ...notFood(), is_food: true, dish_name: 'Ugali', calories_kcal: 780 };

    expect(sanitise(dish)).toEqual(dish);
  });

  it('strips macros off anything that is not food, whatever the model said', () => {
    const notADish = {
      ...notFood(),
      is_food: false,
      dish_name: 'a stapler',
      calories_kcal: 450,
      protein_g: 12,
    };

    const result = sanitise(notADish);

    expect(result.calories_kcal).toBe(0);
    expect(result.protein_g).toBe(0);
    expect(result.dish_name).toBe('');
  });
});

describe('the system prompt', () => {
  it('forbids allergen claims, which an image classifier cannot make safely', () => {
    expect(SYSTEM_PROMPT).toMatch(/never make an allergen claim/i);
  });

  it('requires the assumed serving to be stated', () => {
    expect(SYSTEM_PROMPT).toMatch(/assumed_portion/);
  });

  it('tells the model to prefer low confidence over a confident guess', () => {
    expect(SYSTEM_PROMPT).toMatch(/prefer low over a confident guess/i);
  });

  it('forbids inventing macros for a photograph that is not food', () => {
    expect(SYSTEM_PROMPT).toMatch(/do not invent macros/i);
  });
});
