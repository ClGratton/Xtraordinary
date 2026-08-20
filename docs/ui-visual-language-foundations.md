# UI visual-language foundations

Status: mandatory study material for Android UI and X3 display implementation and review.

This is the shared visual-design course for the repository. A reviewer does not pass a control merely because it is visible, clickable, or labelled. The control must use a coherent metaphor, clean geometry, appropriate placement, and the same visual language as the surrounding product.

## 1. Meaning before drawing

Write the interaction in one verb and the object in one noun before choosing a symbol. Use one familiar glyph for one job. A custom glyph must not combine multiple metaphors merely to restate the label.

- The container communicates that an action is available.
- The label names the result.
- An icon may reinforce the object or established action, but it must not introduce a second competing action.
- A directional or spatial cue belongs where the direction or boundary exists. Moving an edge cue into an unrelated content glyph destroys the spatial metaphor.
- If object and direction are both necessary, separate them spatially. Do not superimpose them unless the compound symbol is an established platform convention.

## 2. Icon grammar

Prefer an existing Material/system icon that matches the intended action. Create code-native vector geometry only when the established set has no suitable symbol or the product owns a documented convention. Functional controls never use generated or stock artwork.

Every icon set on a surface shares:

- a common nominal box, normally 24 dp on Android;
- a common optical center and baseline;
- consistent stroke weight, cap treatment, and corner character;
- enough internal negative space to remain legible at 1x and in Quiet grayscale;
- one dominant silhouette.

Reject a glyph when independent strokes touch, become tangent, visually merge, or create an accidental third shape. Maintain at least one stroke width of optical clearance between independent ideas. Do not solve collision by making the entire symbol smaller; simplify or separate the ideas.

## 3. Placement is part of meaning

Placement must explain the relationship between control and content.

- Put object/type cues inside the labelled control they qualify.
- Put boundary, reveal, expand, or directional cues adjacent to the boundary they describe.
- Leading and trailing placement are deliberate: a cue toward a destination or edge belongs on that side; an icon must not be placed on the opposite side merely because it is a library default.
- Keep decorative cues outside the merged accessibility semantics of the labelled control.
- Never create two equal-looking controls for one action. If two visible elements trigger the same action, their shared ownership must be visually unambiguous and accessibility must expose one action.

## 4. Affordance quality, not affordance presence

A visible pill is not automatically good design. Judge the entire control:

1. **Recognition:** can a person identify it as interactive before reading?
2. **Prediction:** can they predict the result without inventing a metaphor?
3. **Coherence:** do shape, icon, label, placement, and motion describe the same action?
4. **Restraint:** is every mark necessary, or are two cues competing?
5. **Fit:** does it belong to the product's type, shape, color, and motion system?
6. **Optics:** are stroke joins, clearances, centering, and visual weight clean at actual size?

Any failed item blocks approval even when semantics, target size, contrast, and click handling pass.

Contextual header actions that begin a task, import content, or mutate screen state must retain a recognizable control silhouette and one coherent object/action cue during the source-blind five-second inventory. Bare text that can be mistaken for a heading, status, or passive link is a blocking affordance failure. This rule does not turn ordinary navigation links into filled buttons; the container strength must match the action's consequence and place in the hierarchy.

### Viewport ownership and action geography

Every visible region of a finite first viewport needs an owner. A tall phone must not produce a large unassigned band merely because a card or control reached a fixed maximum height. Assign additional height to the surface intended to breathe, enlarge useful preview/content space, or preserve a deliberate bounded rhythm. Lists and naturally scrolling documents remain content-sized; do not stretch rows simply to fill a screen.

Primary action groups on sibling setup or step pages occupy a stable lower action zone whenever the measured content fits. Do not let each page's content height pull its buttons to an unrelated vertical position. On short screens or at large text, preserve semantic minimum gaps and expand into the existing scroll path rather than clipping, shrinking type, or hiding an action.

Review both directions: crowding is a failure, but so is unassigned slack beneath the last meaningful control. Fixed maximum heights require a documented optical or functional reason and evidence at the shortest and tallest supported viewports.

## 5. Required critique passes

Review rendered evidence before source and perform all of these passes:

- **Five-second inventory:** identify controls and predict outcomes without labels or source.
- **Metaphor pass:** name what each glyph depicts and why that depiction matches the action.
- **Collision pass:** inspect the icon at actual size and 2x; reject touching, tangencies, cramped negative space, or accidental composite silhouettes.
- **Placement pass:** cover the icon, then the label, then the edge cue in turn. Each remaining element must still have a coherent role and none may contradict another.
- **System pass:** compare stroke, corner, size, fill, and tonal weight with adjacent controls.
- **Quiet pass:** verify the hierarchy survives grayscale without relying on shadow or subtle adjacent tones.
- **Adaptive pass:** verify compact width and 1.3x/2x text do not squeeze, detach, or reorder the cue.

The reviewer records what failed, not just that a screenshot looked acceptable. A source-blind PASS must include the predicted action, the interpreted metaphor, and an explicit collision/negative-space verdict.

## 6. Implementation discipline

- Use one reusable component for the same action on matching states or faces.
- Keep decorative vector cues separate from semantic button content.
- A directional mark must belong visibly to a control, path, or physical boundary. Never float a bare chevron beside a control as an unexplained second hint; attach it to the boundary it describes or omit it.
- Encode target size, icon box, gap, and placement as purpose-named tokens or component policy.
- Add geometry tests for non-overlap and stable placement when a cue depends on an edge, seam, or boundary.
- Screenshot evidence supplements, but does not replace, semantic and geometry assertions.
- Compare at least the default and Quiet renders before acceptance.

## 7. Rejection examples

Block the candidate when:

- two symbols are overlaid and read as one malformed symbol;
- a paper/document metaphor is used for a code, details, or turn action without product meaning;
- an edge or direction cue is pulled into the middle of a generic button;
- a bare chevron or punctuation mark floats near a control without being attached to the control, path, seam, or boundary it describes;
- icon placement contradicts the direction it communicates;
- a reviewer says only "the button is visible" without judging metaphor and optical construction;
- the label is doing all the explanatory work while the icon predicts a different result;
- a task-starting contextual header action is visually indistinguishable from passive header text;
- a decorative cue becomes a second accessibility target for the same action.

## Primary references

- [Android images and graphics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/images-graphics): vector-first small assets, 24 dp icon treatment, intrinsic padding, and consistent sizing.
- [Android accessibility for apps](https://developer.android.com/guide/topics/ui/accessibility/apps.html): simple controls, 48 dp minimum targets, purpose-based descriptions, and decorative-icon semantics.
- [Android grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units): 4 dp alignment for icons and 8 dp component/layout rhythm.
