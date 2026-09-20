# KulaPro scanner

A Cloudflare Worker that turns a photograph of a dish into a nutrition estimate.

It exists so the Anthropic API key never ships in the APK. A key in an Android app is
extractable in minutes with `apktool`, and whoever extracts it spends your money with no
ceiling. Everything else this worker does, the shared cache and the daily quota, follows
from the same place: a client cannot be trusted to limit itself.

Cloudflare rather than Cloud Functions because it needs no billing account. The free tier
covers this workload several times over.

## What it does, in order

1. Verifies the caller's Firebase ID token against Google's published certificates, and
   checks it was minted for this Firebase project. A token for somebody else's project is
   refused.
2. Hashes the image and checks the cache. A dish someone has already scanned is answered
   for free, and does not count against anyone's quota.
3. Counts the scan against the caller's day. The ceiling stops a client bug, or one
   determined user, running up an unbounded bill.
4. Calls Claude with a structured output schema, so the Android client deserialises a fixed
   contract rather than parsing prose.
5. Strips macros off anything the model did not identify as food, whatever the model said,
   and caches the result.

## Deploying

You need a Cloudflare account (the free plan is enough) and an Anthropic API key.

```sh
cd worker
npm install

# Log in, once.
npx wrangler login

# Create the namespace that holds cached results and the daily counters, then paste the id
# it prints into wrangler.toml.
npx wrangler kv namespace create SCANNER

# The API key. A secret, so it is never in wrangler.toml and never in git.
npx wrangler secret put ANTHROPIC_API_KEY

npm test
npm run deploy
```

`deploy` prints the worker's URL. Put it in the project's `local.properties`, which is not
committed:

```
scanner.url=https://kulapro-scanner.<your-subdomain>.workers.dev
```

Without that line the app builds and the scanner button simply does not appear, so a
checkout with no Cloudflare account still works.

## Choosing the model

`ANTHROPIC_MODEL` in `wrangler.toml`. `claude-opus-5` reads a plate more accurately;
`claude-haiku-4-5` is roughly a fifth of the price. Switching is a config change and a
redeploy, so the trade can be made against real photographs rather than guessed at.

A cache miss is one image of about 1,600 tokens, a cached system prompt, and a short JSON
response. A cache hit costs nothing.

## What it deliberately does not do

It does not answer allergen questions, and the system prompt forbids the model from trying.
An image classifier is not an allergen test, and someone with a real allergy acting on one
could be badly hurt. Those questions are routed to the restaurant.

It does not claim precision it cannot have. Portion size is not measurable from one
photograph, so every result carries the serving it was calculated against and a confidence
the app is required to show.
