Alpha Warning: This update changes a lot of internal things, and is highly experimental. Make sure to report any issues found on github. I expect there to be quite a few,
especially regarding the new item and fluid transfer/interaction APIs. Don't go putting this version into you published modpack / long term survival world yet.

This change finally allows Oritech to move away from forgified fabric API on neoforge. While it was working great in most cases (and allowed Oritech to move to neoforge in the first place), 
there was a lot of overlap with architectury and having one fewer dependency is always a good thing.

- Remove dependency on Fabric API (or forgified fabric API). All fabric api usages have been moved to either architectury or custom events.
- Move fluid and item APIs to new internal crossplatform API. This should improve compatibility with other mods on Neoforge.
- Improve block and item fluid handling. 
- Tanks (and fluid container blocks) can now be filled/emptied by right clicking with a fluid container from any mod. This can also be automated with its inventory slots.
- Allow portable tank items to be used as fluid container when interacting with other mods. Same with portable energy storages.
- Improve steam engine chaining behaviour. The interactions are now also much clearer on this.
- Oritech recipes now operate shapeless, so you no longer need to insert materials into the right slots in assemblers and atomic forges anymore. It can also take multiple items from the same slot.
- Add compat recipes for Clutter, Actually Additions, Applied Energistics 2, Create, Ender IO, Energized Power, Immersive Engineering, Industrial Foregoing, Mekanism (and Mekanism Generators), 
PneumaticCraft: Repressurized, Powah!, Productive Metalworks
- Add compat and neoforge datagen steps (big thanks to @jshipley, this is a huge change, and adds all the compat recipes mentioned!)
- Add "boring nukes" config.
- Add accelerator motor RF capacity config.,
- Allow machine frames to connect to tag-configured support blocks (such as metal beams).
- Use FakePlayer class for destroyer and enderic laser blocks.
- Update translations from crowdin.
- Allow player portal to teleport across dimensions, and fix some crashes with it (@forderian).
- Add Netherwart / cocoa crop filter support (@jshipley).
- Fix enderic lasers not perfectly filling some energy storages (@jshipley).
- Fix stabilized enchanter causing JEI crashes (@jshipley).
- Fix numbers in addon tooltips  (@Tctiancan).
- Fix augments not being properly synced to client after dimension change (@zeng)
- And a ton of additional smaller improvements.