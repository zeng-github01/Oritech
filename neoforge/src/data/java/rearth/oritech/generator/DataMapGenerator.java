package rearth.oritech.generator;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import rearth.oritech.Oritech;

import java.util.concurrent.CompletableFuture;

public class DataMapGenerator extends DataMapProvider {
    public DataMapGenerator(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }
    
    @Override
    protected void gather(HolderLookup.Provider provider) {
        
        for (var pair : Oritech.COMPOSTABLES_DATA) {
            builder(NeoForgeDataMaps.COMPOSTABLES)
              .add(pair.getA().asItem().builtInRegistryHolder(), new Compostable(pair.getB(), true), false);
        }
    }
}