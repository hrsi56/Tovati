package com.yv.bbttracker.data.local.converter

import androidx.room.TypeConverter
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.SexualContact

class RoomConverters {
    @TypeConverter fun measurementSiteToString(value: MeasurementSite): String = value.name
    @TypeConverter fun stringToMeasurementSite(value: String): MeasurementSite = MeasurementSite.valueOf(value)
    @TypeConverter fun measurementSourceToString(value: MeasurementSource): String = value.name
    @TypeConverter fun stringToMeasurementSource(value: String): MeasurementSource = MeasurementSource.valueOf(value)
    @TypeConverter fun bleedingToString(value: BleedingLevel): String = value.name
    @TypeConverter fun stringToBleeding(value: String): BleedingLevel = BleedingLevel.valueOf(value)
    @TypeConverter fun mucusToString(value: CervicalMucus): String = value.name
    @TypeConverter fun stringToMucus(value: String): CervicalMucus = CervicalMucus.valueOf(value)
    @TypeConverter fun mucusSensationToString(value: MucusSensation): String = value.name
    @TypeConverter fun stringToMucusSensation(value: String): MucusSensation = MucusSensation.valueOf(value)
    @TypeConverter fun lhToString(value: LhResult): String = value.name
    @TypeConverter fun stringToLh(value: String): LhResult = LhResult.valueOf(value)
    @TypeConverter fun painToString(value: OvulationPain): String = value.name
    @TypeConverter fun stringToPain(value: String): OvulationPain = OvulationPain.valueOf(value)
    @TypeConverter fun libidoToString(value: LibidoLevel): String = value.name
    @TypeConverter fun stringToLibido(value: String): LibidoLevel = LibidoLevel.valueOf(value)
    @TypeConverter fun sexualContactToString(value: SexualContact): String = value.name
    @TypeConverter fun stringToSexualContact(value: String): SexualContact = SexualContact.valueOf(value)
}
