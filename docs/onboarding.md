# First run

A device that has never finished the first run and has no account opens on it instead of the
Inbox. It has six steps, and the count is fixed, so the progress at the top never changes its
total. The launch animation does not play over it.

| Step | What it does |
| --- | --- |
| 1. Welcome | Names the app, and links to which servers work. |
| 2. Connect | Signs in to a JMAP account with the shared account form. The run cannot go on without one. |
| 3. Privacy | Picks a preset, Full privacy or Convenient, which fills in the privacy settings. |
| 4. Notifications | Picks how new mail reaches the device, then asks for the permission to show alerts. |
| 5. Look | Picks a shipped theme, or colors made from one hue. |
| 6. Ready | Lists the choices; each row opens its step again. |

Every step after the sign-in saves its choice as it is made, so leaving early loses nothing. A run
left after the sign-in continues at the same step when Android restores the app, and otherwise
counts as finished at the next launch, because an account exists.

## When it shows

`MailBehavior.onboarded` is false until the run finishes. A store that never saved it reads false;
a `MailBehavior()` that was never saved reads true, so tests and hosts without storage open on the
Inbox. `rememberOnboarding` starts the run once the saved accounts have loaded and there are none.
A device with an account was set up before the first run existed, so it is marked finished without
seeing it.

The folder color nudge stays hidden for the whole launch the run ran in.

## The steps' parts

`OnboardingScreen` (`feature/onboarding`) draws the steps inside Core's `OnboardingFrame`, which
holds the back chevron's slot and `StepProgress` for the whole run. The progress therefore keeps
its place and width on every step, its fill animates from step to step, and its number rolls.

The onboarding feature imports no other feature. `OnboardingSlots`, filled in `app/OnboardingPage.kt`,
hold what other features draw: the privacy rows (`privacyRows`), the delivery choices
(`DeliveryChoices`), and the hue slider (`ThemeHueSlider`).

## Privacy presets

`PrivacyPreset.applyTo` returns the saved settings with one preset's values. It is a pure function,
so the first run, the Preset row on Settings › Privacy & security, and the "Edited" note all use it:
settings match a preset while `preset.applyTo(behavior) == behavior`.

| Setting | Full privacy | Convenient |
| --- | --- | --- |
| Load images | Never | In your contacts |
| Photo details | Always remove | Ask each time |
| Website icons | Off | On, from DuckDuckGo |
| Photos from all accounts | Off | On |
| Screen security | On | Off |
| Incognito keyboard | On | On |
| Alert previews | New mail only | Use device setting |

Full privacy leaves the icon source alone, so a Bulwark server the user set up is still chosen when
they turn icons back on. Neither preset changes the Bulwark address or the allowed senders. Alert
previews belong to the notifications feature, so `app` sets them beside the preset.

Full privacy turns Screen security on at once, so the rest of the run cannot be captured in a
screenshot.

## Notifications

`DeliveryChoices` lists the installed push services and periodic checks. Until the user has a
service, the first load picks one, once: an installed UnifiedPush app under Full privacy, and
Google's service under Convenient where the device offers it. "Allow notifications" asks Android
for `POST_NOTIFICATIONS` on Android 13 and later; "Not now" skips it, and the next launch with an
account asks again. A platform without pushes skips the step.

## Checks

`PrivacyPresetTest` covers each preset's values, what a preset leaves alone, and the match.
`SuggestedDistributorTest` covers the suggested service. The steps were checked on a Pixel 9a.
