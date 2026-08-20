# Appearance policy

Status: binding implementation contract.

Appearance has two independent persisted axes. They must never be collapsed back into one enum, one color choice, or two look-alike chips.

## Light and dark

Light/dark is selected by directly dragging the scene carousel in Settings. The sea/lighthouse scene represents Light; the astronaut/space scene represents Dark. The selected scene controls the app color scheme, system-bar appearance, and the matching X3 artwork preview. The neighboring scene remains partially visible so the carousel is self-evidently draggable.

The scene is the selector. Do not add a redundant Light/Dark chip row beside it. Programmatic state restoration may scroll the carousel, but a settled user drag owns the persisted choice. Drag remains the primary visual interaction; tapping, keyboard-activating, or switch-activating either visible scene is the equivalent accessible selection route. The carousel exposes one mutually exclusive radio group, not two unrelated image buttons.

## Expressive and Minimal

Expressive/Minimal is a separate visual-style choice below the scene carousel. It does not select light or dark.

- Expressive uses the rounded shape scale, normal spacious rhythm, decorative page motion, and animated destination transitions.
- Minimal uses a tighter shape scale and content spacing, removes nonessential pager wobble/scaling and onboarding decoration motion, and changes primary-destination transitions immediately.
- Both styles keep the same information, actions, readable type sizes, accessibility semantics, and minimum touch targets.

Minimal must materially reduce density, decorative shape, motion, and visual noise. Recoloring the Expressive layout does not satisfy this contract, and dark mode alone is never called Minimal.

## Persistence and migration

Store color mode and visual style under independent keys. Legacy `Expressive` restores Light + Expressive. Legacy `Quiet` restores Dark + Minimal so an existing user's prior appearance remains recognizably close while the new controls become independent.

## Evidence

Required evidence covers Light + Expressive, Dark + Expressive, Light + Minimal, and Dark + Minimal; restart restoration for both axes; direct drag settlement; reduced-motion behavior; and large-text/compact Settings layouts. One screenshot of a color change does not prove Minimal behavior.
