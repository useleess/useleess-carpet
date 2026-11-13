# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- `/customrecipe` makes furnace and brewing stand recipes too (#7, #8): the arrow in the editor is now a recipe type
  button (crafting table / furnace / brewing stand). Furnace = one item in, the result out; brewing stand = an ingredient
  on a bottle or potion (the potion type counts). Real furnaces, brewing stands and hoppers use them with plain
  clients; vanilla smelting and brewing are unchanged, and a recipe clashing with a vanilla one is refused.
- The item picker now lists every potion type (before: one potion of each kind).
- Carpet bots can sleep through the night by themselves (#11): `/autosleep <bot> on` and the bot goes to bed every
  night in a free bed within 3 blocks, so a server with only bots online still skips the night. Kept over a restart.
- Fixed a server crash (#12): when a recipe got unlocked for a player whose connection was already gone, the server
  crashed ("Ticking player", RecipeBookServer). Now that message is skipped; the recipe stays unlocked and shows up at
  the player's next join. The server log names the player and where it came from.
- The `/customrecipe` editor is roomier (#9): a big 6-row window with 54 items per page on top, and the grid and buttons
  below where your inventory normally is. The saved-recipes list shows 54 per page too.
