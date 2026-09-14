package fr.ascendant.renaissance.mekanism;

import java.util.List;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.content.entangloporter.InventoryFrequency;

/** Implemented by the QE mixin; no accessor for its raw storage. */
public interface EndpointChemicalAccess {
    List<IChemicalTank> ascendant$chemicalViews(InventoryFrequency frequency, List<IChemicalTank> tanks);
}
