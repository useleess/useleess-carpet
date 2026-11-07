# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- `/customrecipe` makes furnace and brewing stand recipes too (#7, #8): the arrow in the editor is now a recipe type
  button (crafting table / furnace / brewing stand). Furnace = one item in, the result out; brewing stand = an ingredient
  on a bottle or potion (the potion type counts). Real furnaces, brewing stands and hoppers use them with plain
  clients; vanilla smelting and brewing are unchanged, and a recipe clashing with a vanilla one is refused.
- The item picker now lists every potion type (before: one potion of each kind).
