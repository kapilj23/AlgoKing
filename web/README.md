# `web/` — the privacy policy, as a page

**The policy is published, on Google Sites:**

> https://sites.google.com/view/algoking-privacy/home

That is the URL for the Play listing, and the one the app's Settings → Privacy
policy screen links to (`POLICY_URL`). It carries the same fourteen sections, the
same effective date and the same contact address as the in-app copy.

**This directory is the source text and a self-hosted alternative**, not the live
policy. Keep it in step with the other two copies; if the policy ever moves off
Google Sites, this is what gets deployed and `POLICY_URL` is what changes.

```
web/
└── privacy-policy/
    └── index.html      → https://<your-domain>/privacy-policy/
```

One static file, no build step, no dependencies. Any static host works —
Cloudflare Pages, GitHub Pages, Netlify — with `web/` as the output directory.

## Deploying

**Cloudflare Pages** — connect the repository, set the build command to none and
the output directory to `web`. **GitHub Pages** — publish from a branch with
`/web` as the folder, or copy the directory into a `gh-pages` branch.

Then paste the resulting `/privacy-policy/` URL into Play Console under
**Policy → App content → Privacy policy**.

## Keeping three copies in step

| Copy | Lives in |
|---|---|
| Published | Google Sites, at `POLICY_URL` |
| In-app | `feature/settings/SettingsScreen.kt` → `PrivacyPolicyScreen` |
| Source | `web/privacy-policy/index.html` |

The in-app one is primary for a learner: the lessons make no network calls, so a
policy that needed a connection to read would be the only part of the product that
did. The published one is what the store links to.

The effective date and contact address appear in all three —
`POLICY_EFFECTIVE_DATE` and `POLICY_CONTACT` in Kotlin, the `.effective` line and
the Contact section here, and the same two on the Sites page. **Nothing checks that
they agree**, so changing one means changing all three the same day.

That is not a hypothetical. The copy this replaced claimed the app had no ads while
AdMob was already built, and said progress existed nowhere but the device while
Android Auto Backup was copying it to the learner's Drive. Each was true when
written and neither was revisited.

## Age wording

Deliberately defers to the Play listing rather than asserting a rating — that is
set in Play Console and cannot be verified from this repository.
