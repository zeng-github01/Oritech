Warning: This update is marked as alpha/experimental again, due to a large number of networking changes. Please wait a bit before updating your long-term worlds with this,
as I expect quite a few bugs that I have missed with the new networking implementation.
- Redo entire networking. This shouldn't change much for users, but some machines, like the machine gantries, are now slightly smoother. Also, less network traffic is sent overall, and the networking is now
based on a custom cross-platform networking, instead of owo-lib networking.
- Rework addon / extender system. Addons are now additive, and machine quality now is used for the total amount of extenders that can be added to machines. This makes the first few upgrades
stronger, but doesn't scale as hard with high addon counts.
- Add storage block tags to blocks, not just block items
- Add UI label to fragment forge when yield addon is installed
- Add config setting for steam type
- Add missing tag translations
- Add hyper enchanting exponential scaling factor
- Add pump redstone toggle.
- Change speed addon recipe to work with any steel tag, not just biosteel.
- Increase most machine speeds.
- Slightly remove microcrafting required by removing fire wire items.
- Change refinery logic to only output increased items/fluids if the original recipe has more than 1 output
- Nerf refinery ore processing
- Fix luck augment granting too high stats
- Fix cooler block issues with processing chambers
- Mark ore generation settings as fabric only
- Fix feature biome locators for end platinum and desert oil
- Fix reactor energy port RF/t being hardcoded in tooltips
- Reduce ore amounts in ore boulders
- Update translations from crowdin
- Fix some other small bugs
- Fix steam boiler crash when used with non-generator blocks and then interacted with pipes
- Fix bucket-abuse in fluid-based generators
- Fix oritech storages not implementing SlottedStorage on fabric
- Remove default empty fluid component from tank items
- Fix machine gantry arm not transparent on neoforge