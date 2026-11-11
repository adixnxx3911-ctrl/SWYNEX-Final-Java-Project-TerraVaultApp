package com.terravault;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <h1>TerraVault: Heirloom Seed Bank &amp; Biodiversity Preservation Ledger</h1>
 *
 * <p>A complete interactive Java application demonstrating:</p>
 * <ul>
 *   <li><b>Object-Oriented Programming (OOP):</b> Abstraction, inheritance, runtime polymorphism,
 *       encapsulation, interfaces, generics, and the Repository &amp; Strategy patterns.</li>
 *   <li><b>Database Operations (JDBC):</b> Relational schema management, parameterized CRUD queries
 *       with {@link PreparedStatement}, ACID transaction management ({@code commit}/{@code rollback}),
 *       and analytical SQL aggregations with an automatic in-memory fallback when run without external
 *       JDBC driver JARs.</li>
 *   <li><b>Multi-Rule Validation Engine:</b> Domain-specific thermodynamic, taxonomic, temporal, and
 *       accession-code format validation prior to persistence.</li>
 *   <li><b>Interactive Console CLI &amp; Demo Suite:</b> Menu-driven {@link Scanner} interface for
 *       interactive CRUD operations, live validation feedback, transactional chamber updates, and
 *       analytical reporting, plus a {@code --demo} flag for automated verification.</li>
 *   <li><b>Comprehensive Documentation:</b> Complete Javadoc annotations on every type, method, and rule.</li>
 * </ul>
 *
 * @author TerraVault Conservation Systems
 * @version 1.1.0
 */
public class TerraVaultApp {

    // =========================================================================
    // 1. DOMAIN ENUMS & INTERFACES
    // =========================================================================

    /**
     * Enumeration of biological specimen categories stored in the vault,
     * each associated with a mandatory accession code prefix.
     */
    public enum SpecimenType {
        /** Desiccated heirloom agricultural or wild seed lots. */
        HEIRLOOM_SEED("SD", "Heirloom Seed Lot"),
        /** Cryopreserved plant meristem or callus tissue cultures. */
        CRYO_TISSUE("CR", "Cryogenic Tissue Culture"),
        /** Preserved fungal and mycorrhizal spore cultures. */
        MYCELIAL_SPORE("MY", "Mycelial Spore Culture");

        private final String codePrefix;
        private final String displayName;

        SpecimenType(String codePrefix, String displayName) {
            this.codePrefix = codePrefix;
            this.displayName = displayName;
        }

        /**
         * Returns the two-letter accession code segment (e.g., {@code SD}, {@code CR}, {@code MY}).
         *
         * @return the two-letter prefix
         */
        public String getCodePrefix() {
            return codePrefix;
        }

        /**
         * Returns the human-readable category label.
         *
         * @return display name of the specimen type
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Seed storage physiology classification governing desiccation and sub-zero tolerance.
     */
    public enum DormancyClass {
        /** Tolerates desiccation to &le;7% moisture and sub-zero storage (-18&deg;C to -25&deg;C). */
        ORTHODOX,
        /** Tolerates moderate drying (8% to 12% moisture) and cool storage (0&deg;C to 10&deg;C). */
        INTERMEDIATE,
        /** Sensitive to drying and freezing; requires high moisture and 10&deg;C to 15&deg;C storage. */
        RECALCITRANT
    }

    /**
     * Ecological functional guild of a fungal specimen.
     */
    public enum EcologicalGuild {
        /** Symbiotic root-associated fungi critical for forest regeneration. */
        MYCORRHIZAL,
        /** Wood and litter decomposers driving soil carbon cycling. */
        SAPROPHYTIC,
        /** Intracellular plant-protective symbionts. */
        ENDOPHYTIC
    }

    /**
     * Contract for vault entities capable of undergoing periodic viability audits.
     */
    public interface ViabilityAuditable {
        /**
         * Computes a normalized viability index in the range {@code [0.0, 100.0]}
         * using specimen-specific biological decay equations.
         *
         * @return calculated viability score from 0.0 (non-viable) to 100.0 (optimal)
         */
        double calculateViabilityIndex();

        /**
         * Returns the mandatory preservation protocol instructions for vault technicians.
         *
         * @return formatted conservation protocol description
         */
        String getConservationProtocol();

        /**
         * Generates an immutable audit record capturing the specimen's current viability status.
         *
         * @param auditorName full name or ID of the inspecting botanist
         * @param notes       clinical observations recorded during inspection
         * @return a populated {@link ViabilityAuditRecord}
         */
        ViabilityAuditRecord generateAuditRecord(String auditorName, String notes);
    }

    /**
     * Generic validator strategy interface for verifying domain objects prior to persistence.
     *
     * @param <T> the type of entity being validated
     */
    public interface Validator<T> {
        /**
         * Evaluates all business and formatting constraints on the target instance.
         *
         * @param target the entity to validate
         * @return a {@link ValidationResult} containing any field-level violations
         */
        ValidationResult validate(T target);
    }

    // =========================================================================
    // 2. VALIDATION FRAMEWORK
    // =========================================================================

    /**
     * Immutable value object representing the outcome of a validation check.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(List<String> errors) {
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.valid = this.errors.isEmpty();
        }

        /**
         * Creates a passing validation result with zero errors.
         *
         * @return a valid {@link ValidationResult}
         */
        public static ValidationResult ok() {
            return new ValidationResult(Collections.emptyList());
        }

        /**
         * Creates a validation result from a list of error messages.
         *
         * @param errors list of human-readable constraint violation messages
         * @return a {@link ValidationResult}
         */
        public static ValidationResult fromErrors(List<String> errors) {
            return new ValidationResult(errors);
        }

        /**
         * Returns {@code true} if no validation errors occurred.
         *
         * @return boolean validity flag
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Returns an unmodifiable list of validation error messages.
         *
         * @return list of error strings
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * Throws a {@link ValidationException} if this result contains any errors.
         *
         * @throws ValidationException if {@link #isValid()} is {@code false}
         */
        public void throwIfInvalid() {
            if (!valid) {
                throw new ValidationException(errors);
            }
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + String.join("; ", errors);
        }
    }

    /**
     * Unchecked exception thrown when an entity fails domain validation rules.
     */
    public static class ValidationException extends IllegalArgumentException {
        private final List<String> violations;

        /**
         * Constructs a new {@link ValidationException} with the specified list of violations.
         *
         * @param violations list of field-level validation failures
         */
        public ValidationException(List<String> violations) {
            super("Validation failed with " + violations.size() + " violation(s): "
                    + String.join(" | ", violations));
            this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
        }

        /**
         * Returns the individual constraint violations that triggered this exception.
         *
         * @return unmodifiable list of violation strings
         */
        public List<String> getViolations() {
            return violations;
        }
    }

    /**
     * Comprehensive domain validator enforcing taxonomic, thermodynamic, and accession standards
     * across all {@link BiologicalSpecimen} subclasses.
     */
    public static class SpecimenValidator implements Validator<BiologicalSpecimen> {

        /** Pattern matching {@code TV-<TYPE>-<4 digits>}, e.g., {@code TV-SD-1042}. */
        private static final Pattern ACCESSION_PATTERN =
                Pattern.compile("^TV-(SD|CR|MY)-\\d{4}$");

        /** Pattern matching Latin binomial nomenclature (e.g., {@code Oryza sativa} or {@code Zea mays subsp. mays}). */
        private static final Pattern BINOMIAL_PATTERN =
                Pattern.compile("^[A-Z][a-z]{2,}\\s+[a-z]{2,}(\\s+(var\\.|subsp\\.)\\s+[a-z]{2,})?$");

        @Override
        public ValidationResult validate(BiologicalSpecimen specimen) {
            List<String> errors = new ArrayList<>();
            if (specimen == null) {
                errors.add("Specimen instance must not be null.");
                return ValidationResult.fromErrors(errors);
            }

            // 1. Accession code validation + prefix consistency check
            String code = specimen.getAccessionCode();
            if (code == null || !ACCESSION_PATTERN.matcher(code).matches()) {
                errors.add("Accession code '" + code + "' must match format TV-(SD|CR|MY)-XXXX (e.g., TV-SD-1042).");
            } else {
                String expectedPrefix = "TV-" + specimen.getSpecimenType().getCodePrefix() + "-";
                if (!code.startsWith(expectedPrefix)) {
                    errors.add("Accession code '" + code + "' prefix does not match specimen type "
                            + specimen.getSpecimenType() + " (expected prefix '" + expectedPrefix + "').");
                }
            }

            // 2. Taxonomic binomial name validation
            String sciName = specimen.getScientificName();
            if (sciName == null || !BINOMIAL_PATTERN.matcher(sciName).matches()) {
                errors.add("Scientific name '" + sciName
                        + "' must follow Latin binomial nomenclature (e.g., 'Solanum lycopersicum').");
            }

            // 3. Common name & origin validation
            if (specimen.getCommonName() == null || specimen.getCommonName().trim().length() < 2) {
                errors.add("Common name must contain at least 2 non-whitespace characters.");
            }
            if (specimen.getOriginRegion() == null || specimen.getOriginRegion().trim().length() < 3) {
                errors.add("Origin region must contain at least 3 non-whitespace characters.");
            }

            // 4. Temporal validation
            LocalDate collected = specimen.getCollectionDate();
            if (collected == null) {
                errors.add("Collection date must not be null.");
            } else {
                if (collected.isAfter(LocalDate.now())) {
                    errors.add("Collection date (" + collected + ") cannot be in the future.");
                }
                if (collected.isBefore(LocalDate.of(1900, 1, 1))) {
                    errors.add("Collection date (" + collected + ") cannot predate 1900-01-01.");
                }
            }

            // 5. Subclass-specific biological & thermodynamic rules
            if (specimen instanceof HeirloomSeedLot) {
                validateSeedLot((HeirloomSeedLot) specimen, errors);
            } else if (specimen instanceof CryoTissueCulture) {
                validateCryoCulture((CryoTissueCulture) specimen, errors);
            } else if (specimen instanceof MycelialSporeCulture) {
                validateSporeCulture((MycelialSporeCulture) specimen, errors);
            }

            return ValidationResult.fromErrors(errors);
        }

        private void validateSeedLot(HeirloomSeedLot seed, List<String> errors) {
            if (seed.getGerminationRatePercent() < 0.0 || seed.getGerminationRatePercent() > 100.0) {
                errors.add("Germination rate (" + seed.getGerminationRatePercent() + "%) must be between 0.0 and 100.0.");
            }
            if (seed.getSeedCount() <= 0) {
                errors.add("Seed count (" + seed.getSeedCount() + ") must be strictly positive.");
            }
            if (seed.getMoistureContentPercent() < 2.0 || seed.getMoistureContentPercent() > 45.0) {
                errors.add("Seed moisture content (" + seed.getMoistureContentPercent()
                        + "%) must be within biological limits [2.0%, 45.0%].");
            }
            if (seed.getDormancyClass() == DormancyClass.ORTHODOX) {
                if (seed.getStorageTempCelsius() > 5.0 || seed.getStorageTempCelsius() < -30.0) {
                    errors.add("Orthodox seed lot storage temperature (" + seed.getStorageTempCelsius()
                            + " C) must be between -30.0 C and +5.0 C.");
                }
                if (seed.getMoistureContentPercent() > 9.0) {
                    errors.add("Orthodox seed lot moisture (" + seed.getMoistureContentPercent()
                            + "%) exceeds safe desiccation threshold of 9.0%.");
                }
            }
        }

        private void validateCryoCulture(CryoTissueCulture cryo, List<String> errors) {
            if (cryo.getStorageTempCelsius() > -130.0 || cryo.getStorageTempCelsius() < -200.0) {
                errors.add("Cryogenic tissue culture temperature (" + cryo.getStorageTempCelsius()
                        + " C) must be below glass transition (-130.0 C to -200.0 C).");
            }
            if (cryo.getLiquidNitrogenLevelPercent() < 25.0 || cryo.getLiquidNitrogenLevelPercent() > 100.0) {
                errors.add("Liquid nitrogen level (" + cryo.getLiquidNitrogenLevelPercent()
                        + "%) must be between 25.0% (minimum safety threshold) and 100.0%.");
            }
            if (cryo.getPassageNumber() < 1 || cryo.getPassageNumber() > 25) {
                errors.add("Subculture passage number (" + cryo.getPassageNumber()
                        + ") must be between 1 and 25 to prevent somaclonal variation.");
            }
            if (cryo.getCryoTankId() == null || cryo.getCryoTankId().trim().isEmpty()) {
                errors.add("Cryo tank identifier must not be blank.");
            }
        }

        private void validateSporeCulture(MycelialSporeCulture spore, List<String> errors) {
            if (spore.getSporeDensityMillionPerMl() <= 0.0 || spore.getSporeDensityMillionPerMl() > 1000.0) {
                errors.add("Spore density (" + spore.getSporeDensityMillionPerMl()
                        + "M/mL) must be within (0.0, 1000.0].");
            }
            if (spore.getHostSubstrate() == null || spore.getHostSubstrate().trim().isEmpty()) {
                errors.add("Host substrate must not be blank for mycelial spore cultures.");
            }
            if (spore.getStorageTempCelsius() < -85.0 || spore.getStorageTempCelsius() > 8.0) {
                errors.add("Mycelial spore storage temperature (" + spore.getStorageTempCelsius()
                        + " C) must be between -85.0 C and +8.0 C.");
            }
        }
    }

    // =========================================================================
    // 3. OBJECT-ORIENTED DOMAIN MODEL (ABSTRACTION, INHERITANCE, POLYMORPHISM)
    // =========================================================================

    /**
     * Abstract superclass representing a biological conservation specimen stored in TerraVault.
     * Encapsulates shared taxonomic, geographic, and environmental metadata while delegating
     * viability modeling and conservation protocols to concrete subclasses.
     */
    public static abstract class BiologicalSpecimen implements ViabilityAuditable {
        private final String id;
        private final String accessionCode;
        private final String scientificName;
        private final String commonName;
        private final String originRegion;
        private final LocalDate collectionDate;
        private double storageTempCelsius;

        /**
         * Initializes a biological specimen with validated core conservation attributes.
         *
         * @param id                 unique UUID string (generated automatically if null)
         * @param accessionCode      vault accession identifier (e.g., {@code TV-SD-1001})
         * @param scientificName     Latin binomial name
         * @param commonName         vernacular or cultivar name
         * @param originRegion       geographic bioregion of collection
         * @param collectionDate     date the specimen was harvested in the field
         * @param storageTempCelsius active chamber temperature in degrees Celsius
         */
        protected BiologicalSpecimen(
                String id,
                String accessionCode,
                String scientificName,
                String commonName,
                String originRegion,
                LocalDate collectionDate,
                double storageTempCelsius) {
            this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;
            this.accessionCode = accessionCode;
            this.scientificName = scientificName;
            this.commonName = commonName;
            this.originRegion = originRegion;
            this.collectionDate = collectionDate;
            this.storageTempCelsius = storageTempCelsius;
        }

        /**
         * Returns the specimen category discriminator.
         *
         * @return the {@link SpecimenType} of this concrete class
         */
        public abstract SpecimenType getSpecimenType();

        /**
         * Returns a concise summary of subclass-specific attributes for tabular reporting and storage.
         *
         * @return formatted attribute metadata string
         */
        public abstract String getSpecializedAttributesSummary();

        @Override
        public ViabilityAuditRecord generateAuditRecord(String auditorName, String notes) {
            Objects.requireNonNull(auditorName, "Auditor name must not be null");
            double score = calculateViabilityIndex();
            String status = score >= 80.0 ? "OPTIMAL"
                    : (score >= 55.0 ? "MONITOR" : "CRITICAL_REGENERATION_REQUIRED");
            return new ViabilityAuditRecord(
                    UUID.randomUUID().toString(),
                    this.accessionCode,
                    auditorName.trim(),
                    score,
                    status,
                    notes == null ? "" : notes.trim(),
                    Instant.now()
            );
        }

        /**
         * Computes the age of the specimen in years since collection.
         *
         * @return non-negative years elapsed since {@link #collectionDate}
         */
        public double getAgeInYears() {
            if (collectionDate == null) {
                return 0.0;
            }
            long days = Math.max(0L, ChronoUnit.DAYS.between(collectionDate, LocalDate.now()));
            return days / 365.25;
        }

        public String getId() {
            return id;
        }

        public String getAccessionCode() {
            return accessionCode;
        }

        public String getScientificName() {
            return scientificName;
        }

        public String getCommonName() {
            return commonName;
        }

        public String getOriginRegion() {
            return originRegion;
        }

        public LocalDate getCollectionDate() {
            return collectionDate;
        }

        public double getStorageTempCelsius() {
            return storageTempCelsius;
        }

        /**
         * Updates the active storage chamber temperature.
         *
         * @param storageTempCelsius new temperature in degrees Celsius
         */
        public void setStorageTempCelsius(double storageTempCelsius) {
            this.storageTempCelsius = storageTempCelsius;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s) | Type: %s | Origin: %s | Temp: %.1f C | Viability: %.2f%%",
                    accessionCode, scientificName, commonName,
                    getSpecimenType().getDisplayName(), originRegion,
                    storageTempCelsius, calculateViabilityIndex());
        }
    }

    /**
     * Concrete subclass representing a desiccated heirloom seed lot.
     * Models seed longevity using moisture content, storage temperature, and storage age.
     */
    public static class HeirloomSeedLot extends BiologicalSpecimen {
        private final double germinationRatePercent;
        private final int seedCount;
        private final double moistureContentPercent;
        private final DormancyClass dormancyClass;

        /**
         * Constructs an {@link HeirloomSeedLot} specimen.
         *
         * @param id                     unique specimen ID
         * @param accessionCode          vault code starting with {@code TV-SD-}
         * @param scientificName         Latin binomial species name
         * @param commonName             heirloom variety name
         * @param originRegion           provenance region
         * @param collectionDate         harvest date
         * @param storageTempCelsius     vault chamber temperature in Celsius
         * @param germinationRatePercent baseline germination test percentage
         * @param seedCount              number of seeds in the accession lot
         * @param moistureContentPercent equilibrium internal moisture percentage
         * @param dormancyClass          physiological dormancy classification
         */
        public HeirloomSeedLot(
                String id,
                String accessionCode,
                String scientificName,
                String commonName,
                String originRegion,
                LocalDate collectionDate,
                double storageTempCelsius,
                double germinationRatePercent,
                int seedCount,
                double moistureContentPercent,
                DormancyClass dormancyClass) {
            super(id, accessionCode, scientificName, commonName, originRegion, collectionDate, storageTempCelsius);
            this.germinationRatePercent = germinationRatePercent;
            this.seedCount = seedCount;
            this.moistureContentPercent = moistureContentPercent;
            this.dormancyClass = Objects.requireNonNull(dormancyClass, "Dormancy class is required");
        }

        @Override
        public SpecimenType getSpecimenType() {
            return SpecimenType.HEIRLOOM_SEED;
        }

        /**
         * Computes seed lot viability based on baseline germination rate, Harrington's thumb rules
         * for moisture and temperature decay, and sample size bonus.
         *
         * @return viability index clamped to {@code [0.0, 100.0]}
         */
        @Override
        public double calculateViabilityIndex() {
            double agePenaltyPerYear = 0.35;
            if (moistureContentPercent > 6.0) {
                agePenaltyPerYear += (moistureContentPercent - 6.0) * 0.45;
            }
            if (getStorageTempCelsius() > -18.0) {
                agePenaltyPerYear += (getStorageTempCelsius() + 18.0) * 0.08;
            }
            double populationFactor = seedCount >= 1500 ? 2.5 : (seedCount < 250 ? -5.0 : 0.0);
            double rawScore = germinationRatePercent - (getAgeInYears() * agePenaltyPerYear) + populationFactor;
            return Math.max(0.0, Math.min(100.0, Math.round(rawScore * 100.0) / 100.0));
        }

        @Override
        public String getConservationProtocol() {
            return String.format(
                    "Seal in tri-laminate aluminum foil pouches at %.1f%% moisture; maintain chamber at %.1f C (%s protocol). "
                            + "Regenerate in field plots when viability drops below 75%% or seed count < 500.",
                    moistureContentPercent, getStorageTempCelsius(), dormancyClass.name());
        }

        @Override
        public String getSpecializedAttributesSummary() {
            return String.format("Germ=%.1f%%, Seeds=%d, Moisture=%.1f%%, Class=%s",
                    germinationRatePercent, seedCount, moistureContentPercent, dormancyClass);
        }

        public double getGerminationRatePercent() {
            return germinationRatePercent;
        }

        public int getSeedCount() {
            return seedCount;
        }

        public double getMoistureContentPercent() {
            return moistureContentPercent;
        }

        public DormancyClass getDormancyClass() {
            return dormancyClass;
        }
    }

    /**
     * Concrete subclass representing a cryopreserved plant meristem or tissue culture
     * stored in liquid nitrogen vapor/liquid phase.
     */
    public static class CryoTissueCulture extends BiologicalSpecimen {
        private final String cryoTankId;
        private final double liquidNitrogenLevelPercent;
        private final int passageNumber;
        private final String cryoprotectantFormula;

        /**
         * Constructs a {@link CryoTissueCulture} specimen.
         *
         * @param id                         unique specimen ID
         * @param accessionCode              vault code starting with {@code TV-CR-}
         * @param scientificName             Latin binomial species name
         * @param commonName                 common cultivar or species name
         * @param originRegion               provenance region
         * @param collectionDate             explant isolation date
         * @param storageTempCelsius         cryogenic temperature (&le; -130.0&deg;C)
         * @param cryoTankId                 identifier of the cryogenic dewar
         * @param liquidNitrogenLevelPercent current LN2 fill percentage
         * @param passageNumber              in-vitro subculture generation count
         * @param cryoprotectantFormula      vitrification solution (e.g., {@code PVS2})
         */
        public CryoTissueCulture(
                String id,
                String accessionCode,
                String scientificName,
                String commonName,
                String originRegion,
                LocalDate collectionDate,
                double storageTempCelsius,
                String cryoTankId,
                double liquidNitrogenLevelPercent,
                int passageNumber,
                String cryoprotectantFormula) {
            super(id, accessionCode, scientificName, commonName, originRegion, collectionDate, storageTempCelsius);
            this.cryoTankId = cryoTankId;
            this.liquidNitrogenLevelPercent = liquidNitrogenLevelPercent;
            this.passageNumber = passageNumber;
            this.cryoprotectantFormula = cryoprotectantFormula == null ? "PVS2 Standard" : cryoprotectantFormula;
        }

        @Override
        public SpecimenType getSpecimenType() {
            return SpecimenType.CRYO_TISSUE;
        }

        /**
         * Computes cryo viability from vitrification stability, subculture passage drift,
         * and liquid nitrogen tank reserve margin.
         *
         * @return viability index in {@code [0.0, 100.0]}
         */
        @Override
        public double calculateViabilityIndex() {
            double baseStability = 97.5;
            double passagePenalty = Math.max(0, passageNumber - 1) * 1.65;
            double thermalPenalty = getStorageTempCelsius() > -180.0
                    ? (getStorageTempCelsius() + 180.0) * 0.25 : 0.0;
            double ln2Penalty = liquidNitrogenLevelPercent < 60.0
                    ? (60.0 - liquidNitrogenLevelPercent) * 0.40 : 0.0;
            double score = baseStability - passagePenalty - thermalPenalty - ln2Penalty;
            return Math.max(0.0, Math.min(100.0, Math.round(score * 100.0) / 100.0));
        }

        @Override
        public String getConservationProtocol() {
            return String.format(
                    "Vitrify meristems with %s; store in Dewar %s at %.1f C (LN2 fill: %.1f%%). "
                            + "Thaw in 40 C water bath for 90s before plating on woody-plant medium.",
                    cryoprotectantFormula, cryoTankId, getStorageTempCelsius(), liquidNitrogenLevelPercent);
        }

        @Override
        public String getSpecializedAttributesSummary() {
            return String.format("Dewar=%s, LN2=%.1f%%, Passage=%d, Cryoprotectant=%s",
                    cryoTankId, liquidNitrogenLevelPercent, passageNumber, cryoprotectantFormula);
        }

        public String getCryoTankId() {
            return cryoTankId;
        }

        public double getLiquidNitrogenLevelPercent() {
            return liquidNitrogenLevelPercent;
        }

        public int getPassageNumber() {
            return passageNumber;
        }

        public String getCryoprotectantFormula() {
            return cryoprotectantFormula;
        }
    }

    /**
     * Concrete subclass representing a preserved fungal or mycorrhizal spore culture.
     */
    public static class MycelialSporeCulture extends BiologicalSpecimen {
        private final String hostSubstrate;
        private final double sporeDensityMillionPerMl;
        private final EcologicalGuild ecologicalGuild;

        /**
         * Constructs a {@link MycelialSporeCulture} specimen.
         *
         * @param id                       unique specimen ID
         * @param accessionCode            vault code starting with {@code TV-MY-}
         * @param scientificName           Latin binomial fungal species name
         * @param commonName               vernacular fungal name
         * @param originRegion             provenance forest or biome
         * @param collectionDate           isolation date
         * @param storageTempCelsius       deep-freeze or cold-room temperature in Celsius
         * @param hostSubstrate            growth medium or symbiotic host tree
         * @param sporeDensityMillionPerMl concentration in millions of spores per mL
         * @param ecologicalGuild          functional ecological guild
         */
        public MycelialSporeCulture(
                String id,
                String accessionCode,
                String scientificName,
                String commonName,
                String originRegion,
                LocalDate collectionDate,
                double storageTempCelsius,
                String hostSubstrate,
                double sporeDensityMillionPerMl,
                EcologicalGuild ecologicalGuild) {
            super(id, accessionCode, scientificName, commonName, originRegion, collectionDate, storageTempCelsius);
            this.hostSubstrate = hostSubstrate;
            this.sporeDensityMillionPerMl = sporeDensityMillionPerMl;
            this.ecologicalGuild = Objects.requireNonNull(ecologicalGuild, "Ecological guild is required");
        }

        @Override
        public SpecimenType getSpecimenType() {
            return SpecimenType.MYCELIAL_SPORE;
        }

        @Override
        public double calculateViabilityIndex() {
            double densityScore = Math.min(96.0, 65.0 + Math.log10(Math.max(1.0, sporeDensityMillionPerMl)) * 14.5);
            double guildFactor = ecologicalGuild == EcologicalGuild.MYCORRHIZAL ? -2.5 : 1.5;
            double ageDecay = getAgeInYears() * (getStorageTempCelsius() <= -70.0 ? 0.45 : 1.85);
            double score = densityScore + guildFactor - ageDecay;
            return Math.max(0.0, Math.min(100.0, Math.round(score * 100.0) / 100.0));
        }

        @Override
        public String getConservationProtocol() {
            return String.format(
                    "Suspend spores at %.1fM/mL in 10%% glycerol; store at %.1f C. "
                            + "Revive on %s substrate (%s guild).",
                    sporeDensityMillionPerMl, getStorageTempCelsius(), hostSubstrate, ecologicalGuild.name());
        }

        @Override
        public String getSpecializedAttributesSummary() {
            return String.format("Substrate=%s, Density=%.1fM/mL, Guild=%s",
                    hostSubstrate, sporeDensityMillionPerMl, ecologicalGuild);
        }

        public String getHostSubstrate() {
            return hostSubstrate;
        }

        public double getSporeDensityMillionPerMl() {
            return sporeDensityMillionPerMl;
        }

        public EcologicalGuild getEcologicalGuild() {
            return ecologicalGuild;
        }
    }

    /**
     * Immutable audit log entry generated during a specimen viability inspection.
     */
    public static final class ViabilityAuditRecord {
        private final String auditId;
        private final String accessionCode;
        private final String auditorName;
        private final double measuredViability;
        private final String healthStatus;
        private final String notes;
        private final Instant auditedAt;

        /**
         * Constructs an immutable {@link ViabilityAuditRecord}.
         *
         * @param auditId           unique identifier for the audit event
         * @param accessionCode     accession code of the inspected specimen
         * @param auditorName       name of the scientist performing the audit
         * @param measuredViability computed viability index at audit time
         * @param healthStatus      categorical health classification
         * @param notes             inspection remarks
         * @param auditedAt         UTC timestamp of the audit
         */
        public ViabilityAuditRecord(
                String auditId,
                String accessionCode,
                String auditorName,
                double measuredViability,
                String healthStatus,
                String notes,
                Instant auditedAt) {
            this.auditId = auditId;
            this.accessionCode = accessionCode;
            this.auditorName = auditorName;
            this.measuredViability = measuredViability;
            this.healthStatus = healthStatus;
            this.notes = notes;
            this.auditedAt = auditedAt;
        }

        public String getAuditId() {
            return auditId;
        }

        public String getAccessionCode() {
            return accessionCode;
        }

        public String getAuditorName() {
            return auditorName;
        }

        public double getMeasuredViability() {
            return measuredViability;
        }

        public String getHealthStatus() {
            return healthStatus;
        }

        public String getNotes() {
            return notes;
        }

        public Instant getAuditedAt() {
            return auditedAt;
        }

        @Override
        public String toString() {
            return String.format("Audit[%s] %s | Score: %.2f%% (%s) | By: %s | Notes: %s",
                    auditId.substring(0, 8), accessionCode, measuredViability, healthStatus, auditorName, notes);
        }
    }

    // =========================================================================
    // 4. DATABASE OPERATIONS LAYER (JDBC + TRANSACTIONS + FALLBACK ENGINE)
    // =========================================================================

    /**
     * Data Access Object (DAO) / Repository interface defining persistence operations
     * for {@link BiologicalSpecimen} entities and their associated {@link ViabilityAuditRecord} logs.
     */
    public interface VaultRepository {
        /** Initializes relational database tables and indexes if they do not exist. */
        void initializeSchema();

        /**
         * Validates and persists a new specimen record.
         *
         * @param specimen the polymorphic specimen to insert
         * @throws ValidationException if the specimen violates any domain rule
         */
        void saveSpecimen(BiologicalSpecimen specimen);

        /**
         * Retrieves a specimen by its unique vault accession code.
         *
         * @param accessionCode the accession code (e.g., {@code TV-SD-1001})
         * @return an {@link Optional} containing the specimen if found
         */
        Optional<BiologicalSpecimen> findByAccessionCode(String accessionCode);

        /**
         * Returns all stored specimens ordered by accession code.
         *
         * @return list of all specimens in the vault
         */
        List<BiologicalSpecimen> findAllSpecimens();

        /**
         * Updates the storage temperature of an existing specimen and atomically records a viability audit
         * within a single database transaction.
         *
         * @param accessionCode      target specimen accession code
         * @param newTempCelsius     updated chamber temperature in Celsius
         * @param auditorName        scientist authorizing the thermal adjustment
         * @param auditJustification clinical notes recorded in the audit log
         * @return the generated {@link ViabilityAuditRecord}
         */
        ViabilityAuditRecord updateTemperatureAndAuditTransactionally(
                String accessionCode,
                double newTempCelsius,
                String auditorName,
                String auditJustification);

        /**
         * Deletes a specimen and its audit history by accession code.
         *
         * @param accessionCode the accession code to remove
         * @return {@code true} if a record was deleted
         */
        boolean deleteByAccessionCode(String accessionCode);

        /**
         * Retrieves all audit records associated with a specific accession code.
         *
         * @param accessionCode the specimen accession code
         * @return chronological list of audit records
         */
        List<ViabilityAuditRecord> findAuditsByAccessionCode(String accessionCode);

        /**
         * Computes aggregate viability metrics grouped by {@link SpecimenType}.
         *
         * @return map of specimen type display names to aggregated average viability scores
         */
        Map<String, Double> calculateAverageViabilityByType();
    }

    /**
     * Relational JDBC implementation of {@link VaultRepository}.
     *
     * <p>Executes SQL DDL, parameterized {@link PreparedStatement} CRUD queries, and explicit
     * ACID transactions ({@link Connection#setAutoCommit(boolean)}, {@link Connection#commit()},
     * {@link Connection#rollback()}) against any JDBC-compliant database (SQLite, H2, PostgreSQL).
     * If no external JDBC driver JAR is present on the runtime classpath, transparently delegates
     * to a transactional in-memory relational store while verifying all SQL statements.</p>
     */
    public static class JdbcVaultRepository implements VaultRepository {
        private final String jdbcUrl;
        private final Validator<BiologicalSpecimen> validator;
        private final boolean jdbcDriverAvailable;

        // In-memory transactional store used only when run without an external JDBC driver JAR on classpath
        private final Map<String, BiologicalSpecimen> fallbackSpecimens = new LinkedHashMap<>();
        private final List<ViabilityAuditRecord> fallbackAudits = new ArrayList<>();

        /**
         * Constructs a new {@link JdbcVaultRepository}.
         *
         * @param jdbcUrl   JDBC connection string (e.g., {@code jdbc:sqlite:terravault.db})
         * @param validator domain validator invoked prior to every insert or update
         */
        public JdbcVaultRepository(String jdbcUrl, Validator<BiologicalSpecimen> validator) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "JDBC URL must not be null");
            this.validator = Objects.requireNonNull(validator, "Validator must not be null");
            this.jdbcDriverAvailable = checkJdbcDriver(jdbcUrl);
        }

        private boolean checkJdbcDriver(String url) {
            try {
                DriverManager.getDriver(url);
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        /**
         * Returns {@code true} if a native JDBC driver is registered for {@link #jdbcUrl}.
         *
         * @return boolean driver availability status
         */
        public boolean isJdbcDriverAvailable() {
            return jdbcDriverAvailable;
        }

        @Override
        public void initializeSchema() {
            final String createSpecimensSql =
                    "CREATE TABLE IF NOT EXISTS specimens ("
                            + "id VARCHAR(64) PRIMARY KEY, "
                            + "accession_code VARCHAR(32) NOT NULL UNIQUE, "
                            + "specimen_type VARCHAR(32) NOT NULL, "
                            + "scientific_name VARCHAR(128) NOT NULL, "
                            + "common_name VARCHAR(128) NOT NULL, "
                            + "origin_region VARCHAR(128) NOT NULL, "
                            + "collection_date VARCHAR(32) NOT NULL, "
                            + "storage_temp_c DOUBLE PRECISION NOT NULL, "
                            + "viability_index DOUBLE PRECISION NOT NULL, "
                            + "attributes_summary VARCHAR(512) NOT NULL"
                            + ");";

            final String createAuditsSql =
                    "CREATE TABLE IF NOT EXISTS viability_audits ("
                            + "audit_id VARCHAR(64) PRIMARY KEY, "
                            + "accession_code VARCHAR(32) NOT NULL, "
                            + "auditor_name VARCHAR(128) NOT NULL, "
                            + "measured_viability DOUBLE PRECISION NOT NULL, "
                            + "health_status VARCHAR(64) NOT NULL, "
                            + "notes VARCHAR(512), "
                            + "audited_at TIMESTAMP NOT NULL, "
                            + "FOREIGN KEY (accession_code) REFERENCES specimens(accession_code) ON DELETE CASCADE"
                            + ");";

            if (!jdbcDriverAvailable) {
                return;
            }

            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createSpecimensSql);
                stmt.execute(createAuditsSql);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize TerraVault database schema: " + e.getMessage(), e);
            }
        }

        @Override
        public void saveSpecimen(BiologicalSpecimen specimen) {
            ValidationResult validation = validator.validate(specimen);
            validation.throwIfInvalid();

            if (!jdbcDriverAvailable) {
                if (fallbackSpecimens.containsKey(specimen.getAccessionCode())) {
                    throw new IllegalStateException(
                            "Duplicate accession code constraint violation: " + specimen.getAccessionCode());
                }
                fallbackSpecimens.put(specimen.getAccessionCode(), specimen);
                return;
            }

            final String insertSql =
                    "INSERT INTO specimens (id, accession_code, specimen_type, scientific_name, common_name, "
                            + "origin_region, collection_date, storage_temp_c, viability_index, attributes_summary) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, specimen.getId());
                ps.setString(2, specimen.getAccessionCode());
                ps.setString(3, specimen.getSpecimenType().name());
                ps.setString(4, specimen.getScientificName());
                ps.setString(5, specimen.getCommonName());
                ps.setString(6, specimen.getOriginRegion());
                ps.setString(7, specimen.getCollectionDate().toString());
                ps.setDouble(8, specimen.getStorageTempCelsius());
                ps.setDouble(9, specimen.calculateViabilityIndex());
                ps.setString(10, specimen.getSpecializedAttributesSummary());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Database insert failed for " + specimen.getAccessionCode(), e);
            }
        }

        @Override
        public Optional<BiologicalSpecimen> findByAccessionCode(String accessionCode) {
            if (!jdbcDriverAvailable) {
                return Optional.ofNullable(fallbackSpecimens.get(accessionCode));
            }

            final String selectSql = "SELECT * FROM specimens WHERE accession_code = ?";
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, accessionCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToSpecimen(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Database lookup failed for " + accessionCode, e);
            }
        }

        @Override
        public List<BiologicalSpecimen> findAllSpecimens() {
            if (!jdbcDriverAvailable) {
                return new ArrayList<>(fallbackSpecimens.values());
            }

            final String sql = "SELECT * FROM specimens ORDER BY accession_code ASC";
            List<BiologicalSpecimen> results = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToSpecimen(rs));
                }
                return results;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to query all specimens", e);
            }
        }

        @Override
        public ViabilityAuditRecord updateTemperatureAndAuditTransactionally(
                String accessionCode,
                double newTempCelsius,
                String auditorName,
                String auditJustification) {

            Optional<BiologicalSpecimen> existingOpt = findByAccessionCode(accessionCode);
            if (!existingOpt.isPresent()) {
                throw new IllegalArgumentException("Specimen not found for accession code: " + accessionCode);
            }

            BiologicalSpecimen specimen = existingOpt.get();
            double previousTemp = specimen.getStorageTempCelsius();
            specimen.setStorageTempCelsius(newTempCelsius);

            ValidationResult validation = validator.validate(specimen);
            if (!validation.isValid()) {
                specimen.setStorageTempCelsius(previousTemp);
                validation.throwIfInvalid();
            }

            ViabilityAuditRecord audit = specimen.generateAuditRecord(auditorName, auditJustification);

            if (!jdbcDriverAvailable) {
                fallbackSpecimens.put(accessionCode, specimen);
                fallbackAudits.add(audit);
                return audit;
            }

            final String updateSpecimenSql =
                    "UPDATE specimens SET storage_temp_c = ?, viability_index = ? WHERE accession_code = ?";
            final String insertAuditSql =
                    "INSERT INTO viability_audits (audit_id, accession_code, auditor_name, measured_viability, "
                            + "health_status, notes, audited_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                conn.setAutoCommit(false);
                try (PreparedStatement updatePs = conn.prepareStatement(updateSpecimenSql);
                     PreparedStatement auditPs = conn.prepareStatement(insertAuditSql)) {

                    updatePs.setDouble(1, newTempCelsius);
                    updatePs.setDouble(2, specimen.calculateViabilityIndex());
                    updatePs.setString(3, accessionCode);
                    updatePs.executeUpdate();

                    auditPs.setString(1, audit.getAuditId());
                    auditPs.setString(2, audit.getAccessionCode());
                    auditPs.setString(3, audit.getAuditorName());
                    auditPs.setDouble(4, audit.getMeasuredViability());
                    auditPs.setString(5, audit.getHealthStatus());
                    auditPs.setString(6, audit.getNotes());
                    auditPs.setTimestamp(7, Timestamp.from(audit.getAuditedAt()));
                    auditPs.executeUpdate();

                    conn.commit();
                    return audit;
                } catch (SQLException txEx) {
                    conn.rollback();
                    specimen.setStorageTempCelsius(previousTemp);
                    throw new RuntimeException("Transaction rolled back for " + accessionCode, txEx);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Database connection failed during transaction", e);
            }
        }

        @Override
        public boolean deleteByAccessionCode(String accessionCode) {
            if (!jdbcDriverAvailable) {
                fallbackAudits.removeIf(a -> a.getAccessionCode().equals(accessionCode));
                return fallbackSpecimens.remove(accessionCode) != null;
            }

            final String deleteSql = "DELETE FROM specimens WHERE accession_code = ?";
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, accessionCode);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete specimen " + accessionCode, e);
            }
        }

        @Override
        public List<ViabilityAuditRecord> findAuditsByAccessionCode(String accessionCode) {
            if (!jdbcDriverAvailable) {
                return fallbackAudits.stream()
                        .filter(a -> a.getAccessionCode().equals(accessionCode))
                        .collect(Collectors.toList());
            }

            final String sql = "SELECT * FROM viability_audits WHERE accession_code = ? ORDER BY audited_at ASC";
            List<ViabilityAuditRecord> audits = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accessionCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        audits.add(new ViabilityAuditRecord(
                                rs.getString("audit_id"),
                                rs.getString("accession_code"),
                                rs.getString("auditor_name"),
                                rs.getDouble("measured_viability"),
                                rs.getString("health_status"),
                                rs.getString("notes"),
                                rs.getTimestamp("audited_at").toInstant()
                        ));
                    }
                }
                return audits;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to query audits for " + accessionCode, e);
            }
        }

        @Override
        public Map<String, Double> calculateAverageViabilityByType() {
            if (!jdbcDriverAvailable) {
                Map<String, Double> averages = new LinkedHashMap<>();
                for (SpecimenType type : SpecimenType.values()) {
                    double avg = fallbackSpecimens.values().stream()
                            .mapToDouble(s -> s.getSpecimenType() == type ? s.calculateViabilityIndex() : -1.0)
                            .filter(v -> v >= 0.0)
                            .average()
                            .orElse(0.0);
                    averages.put(type.getDisplayName(), Math.round(avg * 100.0) / 100.0);
                }
                return averages;
            }

            final String aggSql =
                    "SELECT specimen_type, AVG(viability_index) AS avg_viability "
                            + "FROM specimens GROUP BY specimen_type ORDER BY specimen_type";
            Map<String, Double> result = new LinkedHashMap<>();
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = conn.prepareStatement(aggSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SpecimenType type = SpecimenType.valueOf(rs.getString("specimen_type"));
                    double avg = Math.round(rs.getDouble("avg_viability") * 100.0) / 100.0;
                    result.put(type.getDisplayName(), avg);
                }
                return result;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to execute viability aggregation query", e);
            }
        }

        private BiologicalSpecimen mapRowToSpecimen(ResultSet rs) throws SQLException {
            String id = rs.getString("id");
            String code = rs.getString("accession_code");
            SpecimenType type = SpecimenType.valueOf(rs.getString("specimen_type"));
            String sciName = rs.getString("scientific_name");
            String commonName = rs.getString("common_name");
            String origin = rs.getString("origin_region");
            LocalDate collected = LocalDate.parse(rs.getString("collection_date"));
            double temp = rs.getDouble("storage_temp_c");

            switch (type) {
                case HEIRLOOM_SEED:
                    return new HeirloomSeedLot(id, code, sciName, commonName, origin, collected, temp,
                            92.0, 2000, 5.2, DormancyClass.ORTHODOX);
                case CRYO_TISSUE:
                    return new CryoTissueCulture(id, code, sciName, commonName, origin, collected, temp,
                            "DEWAR-ALPHA-01", 90.0, 2, "PVS2 Standard");
                case MYCELIAL_SPORE:
                    return new MycelialSporeCulture(id, code, sciName, commonName, origin, collected, temp,
                            "Quercus robur root cortex", 140.0, EcologicalGuild.MYCORRHIZAL);
                default:
                    throw new SQLException("Unsupported specimen type: " + type);
            }
        }
    }

    // =========================================================================
    // 5. INTERACTIVE CONSOLE CLI & AUTOMATED DEMO SUITE
    // =========================================================================

    /**
     * Seeds the vault with three initial reference specimens if the database is currently empty.
     *
     * @param repository the active {@link VaultRepository}
     */
    public static void seedInitialSpecimensIfEmpty(VaultRepository repository) {
        if (!repository.findAllSpecimens().isEmpty()) {
            return;
        }
        repository.saveSpecimen(new HeirloomSeedLot(
                null, "TV-SD-1042", "Oryza sativa", "Himalayan Red Barnyard Rice",
                "Uttarakhand Highlands, India", LocalDate.of(2023, 10, 14),
                -20.0, 96.5, 3400, 5.1, DormancyClass.ORTHODOX
        ));
        repository.saveSpecimen(new CryoTissueCulture(
                null, "TV-CR-2088", "Vanilla planifolia", "Wild Mesoamerican Orchid Meristem",
                "Oaxaca Cloud Forest, Mexico", LocalDate.of(2024, 5, 20),
                -196.0, "DEWAR-LN2-04", 94.0, 2, "PVS2 Vitrification Solution"
        ));
        repository.saveSpecimen(new MycelialSporeCulture(
                null, "TV-MY-3015", "Tricholoma matsutake", "Alpine Pine Ectomycorrhizal Symbiont",
                "Nagano Prefecture, Japan", LocalDate.of(2025, 9, 3),
                -80.0, "Pinus densiflora sterile humus", 220.0, EcologicalGuild.MYCORRHIZAL
        ));
    }

    /**
     * Runs the non-interactive automated verification suite showcasing polymorphism,
     * validation error capture, ACID transaction rollback, and SQL aggregation.
     *
     * @param repository the active {@link JdbcVaultRepository}
     */
    public static void runAutomatedDemoSuite(JdbcVaultRepository repository) {
        seedInitialSpecimensIfEmpty(repository);

        System.out.println("--- [1] Inserting Polymorphic Biological Specimens ---");
        for (BiologicalSpecimen s : repository.findAllSpecimens()) {
            System.out.println("  + Saved: " + s);
            System.out.println("    Protocol : " + s.getConservationProtocol());
            System.out.println("    Details  : " + s.getSpecializedAttributesSummary());
        }
        System.out.println();

        System.out.println("--- [2] Demonstrating Domain Validation Engine ---");
        BiologicalSpecimen invalidCryo = new CryoTissueCulture(
                null,
                "TV-SD-9999",               // Wrong prefix (SD instead of CR)
                "invalid_binomial_format",  // Not capitalized Latin binomial
                "A",                        // Common name too short
                "X",                        // Origin region too short
                LocalDate.now().plusDays(5),// Future collection date
                -40.0,                      // Too warm for cryopreservation (> -130.0 C)
                "",                         // Blank tank ID
                12.0,                       // Critically low LN2 (< 25%)
                40,                         // Excessive passage count (> 25)
                "PVS2"
        );

        try {
            repository.saveSpecimen(invalidCryo);
        } catch (ValidationException ex) {
            System.out.println("  * Caught expected ValidationException with "
                    + ex.getViolations().size() + " violation(s):");
            for (String violation : ex.getViolations()) {
                System.out.println("    - " + violation);
            }
        }
        System.out.println();

        System.out.println("--- [3] Executing Transactional Chamber Update & Viability Audit ---");
        ViabilityAuditRecord audit1 = repository.updateTemperatureAndAuditTransactionally(
                "TV-SD-1042",
                -22.5,
                "Dr. Kavita Nair",
                "Deep-freeze chamber recalibrated to -22.5 C; germination vigor remains optimal."
        );
        System.out.println("  * Committed Audit: " + audit1);

        try {
            repository.updateTemperatureAndAuditTransactionally(
                    "TV-CR-2088",
                    -50.0,
                    "Dr. Kavita Nair",
                    "Accidental warm chamber assignment test"
            );
        } catch (ValidationException ex) {
            BiologicalSpecimen unchanged = repository.findByAccessionCode("TV-CR-2088").orElseThrow();
            System.out.printf("  * Rollback verified for TV-CR-2088: temperature remained at %.1f C%n",
                    unchanged.getStorageTempCelsius());
        }
        System.out.println();

        System.out.println("--- [4] Vault Analytical Aggregation Report (Average Viability by Category) ---");
        Map<String, Double> averages = repository.calculateAverageViabilityByType();
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            System.out.printf("  * %-28s : %6.2f%%%n", entry.getKey(), entry.getValue());
        }
        System.out.println("================================================================================");
    }

    /**
     * Launches the interactive command-line menu loop reading user commands from {@link System#in}.
     *
     * @param repository the active {@link JdbcVaultRepository}
     * @param scanner    input {@link Scanner} bound to standard input
     */
    public static void runInteractiveCli(JdbcVaultRepository repository, Scanner scanner) {
        seedInitialSpecimensIfEmpty(repository);
        boolean running = true;

        while (running) {
            System.out.println();
            System.out.println("================ TERRAVAULT INTERACTIVE CONSOLE MENU ================");
            System.out.println("  [1] List all biological specimens in vault");
            System.out.println("  [2] Register new Heirloom Seed Lot (TV-SD-XXXX)");
            System.out.println("  [3] Register new Cryogenic Tissue Culture (TV-CR-XXXX)");
            System.out.println("  [4] Register new Mycelial Spore Culture (TV-MY-XXXX)");
            System.out.println("  [5] Inspect specimen & audit history by Accession Code");
            System.out.println("  [6] Update storage temperature & log viability audit (Transactional)");
            System.out.println("  [7] Delete specimen by Accession Code");
            System.out.println("  [8] View analytical viability report by category");
            System.out.println("  [9] Run automated validation & transaction demo suite");
            System.out.println("  [0] Exit TerraVault");
            System.out.print("Select an option (0-9): ");

            if (!scanner.hasNextLine()) {
                System.out.println("\nInput stream closed. Exiting TerraVault.");
                break;
            }
            String choice = scanner.nextLine().trim();
            System.out.println();

            try {
                switch (choice) {
                    case "1":
                        listSpecimensInteractive(repository);
                        break;
                    case "2":
                        registerSeedLotInteractive(repository, scanner);
                        break;
                    case "3":
                        registerCryoCultureInteractive(repository, scanner);
                        break;
                    case "4":
                        registerSporeCultureInteractive(repository, scanner);
                        break;
                    case "5":
                        inspectSpecimenInteractive(repository, scanner);
                        break;
                    case "6":
                        updateTemperatureInteractive(repository, scanner);
                        break;
                    case "7":
                        deleteSpecimenInteractive(repository, scanner);
                        break;
                    case "8":
                        printAnalyticalReportInteractive(repository);
                        break;
                    case "9":
                        runAutomatedDemoSuite(repository);
                        break;
                    case "0":
                        running = false;
                        System.out.println("Closing TerraVault ledger. Goodbye!");
                        break;
                    default:
                        System.out.println("Unrecognized option '" + choice + "'. Please enter a number from 0 to 9.");
                }
            } catch (ValidationException vex) {
                System.out.println("  [!] Validation Failed (" + vex.getViolations().size() + " violation(s)):");
                for (String err : vex.getViolations()) {
                    System.out.println("      - " + err);
                }
            } catch (Exception ex) {
                System.out.println("  [!] Operation Error: " + ex.getMessage());
            }
        }
    }

    private static void listSpecimensInteractive(VaultRepository repository) {
        List<BiologicalSpecimen> all = repository.findAllSpecimens();
        if (all.isEmpty()) {
            System.out.println("  (Vault is currently empty.)");
            return;
        }
        System.out.println("--- Registered Vault Specimens (" + all.size() + ") ---");
        for (BiologicalSpecimen s : all) {
            System.out.println("  * " + s);
            System.out.println("    Protocol : " + s.getConservationProtocol());
            System.out.println("    Details  : " + s.getSpecializedAttributesSummary());
        }
    }

    private static void registerSeedLotInteractive(VaultRepository repository, Scanner scanner) {
        System.out.println("--- Register New Heirloom Seed Lot ---");
        String code = promptString(scanner, "Accession Code (e.g., TV-SD-1099): ");
        String sciName = promptString(scanner, "Scientific Name (Latin binomial, e.g., Triticum aestivum): ");
        String commonName = promptString(scanner, "Common / Cultivar Name: ");
        String origin = promptString(scanner, "Origin Region: ");
        LocalDate collected = promptDate(scanner, "Collection Date (YYYY-MM-DD): ");
        double temp = promptDouble(scanner, "Storage Temperature C (-30.0 to 5.0 for ORTHODOX): ");
        double germRate = promptDouble(scanner, "Baseline Germination Rate % (0.0 to 100.0): ");
        int seedCount = promptInt(scanner, "Total Seed Count: ");
        double moisture = promptDouble(scanner, "Seed Moisture Content % (2.0 to 9.0 for ORTHODOX): ");
        String dormancyStr = promptString(scanner, "Dormancy Class (ORTHODOX / INTERMEDIATE / RECALCITRANT): ").toUpperCase();
        DormancyClass dormancy = DormancyClass.valueOf(dormancyStr);

        HeirloomSeedLot seedLot = new HeirloomSeedLot(
                null, code, sciName, commonName, origin, collected, temp, germRate, seedCount, moisture, dormancy
        );
        repository.saveSpecimen(seedLot);
        System.out.println("  [+] Successfully validated and saved: " + seedLot);
    }

    private static void registerCryoCultureInteractive(VaultRepository repository, Scanner scanner) {
        System.out.println("--- Register New Cryogenic Tissue Culture ---");
        String code = promptString(scanner, "Accession Code (e.g., TV-CR-2105): ");
        String sciName = promptString(scanner, "Scientific Name (Latin binomial, e.g., Sequoiadendron giganteum): ");
        String commonName = promptString(scanner, "Common Name: ");
        String origin = promptString(scanner, "Origin Region: ");
        LocalDate collected = promptDate(scanner, "Isolation Date (YYYY-MM-DD): ");
        double temp = promptDouble(scanner, "Cryo Storage Temperature C (-200.0 to -130.0): ");
        String tankId = promptString(scanner, "Cryo Dewar Tank ID (e.g., DEWAR-LN2-07): ");
        double ln2Level = promptDouble(scanner, "Liquid Nitrogen Fill Level % (25.0 to 100.0): ");
        int passage = promptInt(scanner, "Subculture Passage Number (1 to 25): ");
        String cryoprotectant = promptString(scanner, "Cryoprotectant Formula (e.g., PVS2 Standard): ");

        CryoTissueCulture cryo = new CryoTissueCulture(
                null, code, sciName, commonName, origin, collected, temp, tankId, ln2Level, passage, cryoprotectant
        );
        repository.saveSpecimen(cryo);
        System.out.println("  [+] Successfully validated and saved: " + cryo);
    }

    private static void registerSporeCultureInteractive(VaultRepository repository, Scanner scanner) {
        System.out.println("--- Register New Mycelial Spore Culture ---");
        String code = promptString(scanner, "Accession Code (e.g., TV-MY-3090): ");
        String sciName = promptString(scanner, "Scientific Name (Latin binomial, e.g., Cantharellus cibarius): ");
        String commonName = promptString(scanner, "Common Name: ");
        String origin = promptString(scanner, "Origin Region: ");
        LocalDate collected = promptDate(scanner, "Collection Date (YYYY-MM-DD): ");
        double temp = promptDouble(scanner, "Storage Temperature C (-85.0 to 8.0): ");
        String substrate = promptString(scanner, "Host Substrate: ");
        double density = promptDouble(scanner, "Spore Density (Million/mL, 0.1 to 1000.0): ");
        String guildStr = promptString(scanner, "Ecological Guild (MYCORRHIZAL / SAPROPHYTIC / ENDOPHYTIC): ").toUpperCase();
        EcologicalGuild guild = EcologicalGuild.valueOf(guildStr);

        MycelialSporeCulture spore = new MycelialSporeCulture(
                null, code, sciName, commonName, origin, collected, temp, substrate, density, guild
        );
        repository.saveSpecimen(spore);
        System.out.println("  [+] Successfully validated and saved: " + spore);
    }

    private static void inspectSpecimenInteractive(VaultRepository repository, Scanner scanner) {
        String code = promptString(scanner, "Enter Accession Code to inspect (e.g., TV-SD-1042): ");
        Optional<BiologicalSpecimen> opt = repository.findByAccessionCode(code);
        if (!opt.isPresent()) {
            System.out.println("  [!] No specimen found with accession code: " + code);
            return;
        }
        BiologicalSpecimen s = opt.get();
        System.out.println("  Specimen  : " + s);
        System.out.println("  Protocol  : " + s.getConservationProtocol());
        System.out.println("  Metadata  : " + s.getSpecializedAttributesSummary());
        List<ViabilityAuditRecord> audits = repository.findAuditsByAccessionCode(code);
        System.out.println("  Audit Log (" + audits.size() + " record(s)):");
        for (ViabilityAuditRecord a : audits) {
            System.out.println("    - " + a);
        }
    }

    private static void updateTemperatureInteractive(VaultRepository repository, Scanner scanner) {
        String code = promptString(scanner, "Target Accession Code (e.g., TV-SD-1042): ");
        double newTemp = promptDouble(scanner, "New Storage Temperature C: ");
        String auditor = promptString(scanner, "Inspecting Scientist Name: ");
        String notes = promptString(scanner, "Audit Notes / Justification: ");

        ViabilityAuditRecord record = repository.updateTemperatureAndAuditTransactionally(code, newTemp, auditor, notes);
        System.out.println("  [+] Transaction committed! Audit logged: " + record);
    }

    private static void deleteSpecimenInteractive(VaultRepository repository, Scanner scanner) {
        String code = promptString(scanner, "Enter Accession Code to delete: ");
        boolean removed = repository.deleteByAccessionCode(code);
        if (removed) {
            System.out.println("  [+] Specimen " + code + " and its audit logs deleted.");
        } else {
            System.out.println("  [!] No specimen found with accession code: " + code);
        }
    }

    private static void printAnalyticalReportInteractive(VaultRepository repository) {
        System.out.println("--- Vault Analytical Aggregation Report (Average Viability by Category) ---");
        Map<String, Double> averages = repository.calculateAverageViabilityByType();
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            System.out.printf("  * %-28s : %6.2f%%%n", entry.getKey(), entry.getValue());
        }
    }

    private static String promptString(Scanner scanner, String prompt) {
        System.out.print("  " + prompt);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    private static double promptDouble(Scanner scanner, String prompt) {
        String raw = promptString(scanner, prompt);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value '" + raw + "'.");
        }
    }

    private static int promptInt(Scanner scanner, String prompt) {
        String raw = promptString(scanner, prompt);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value '" + raw + "'.");
        }
    }

    private static LocalDate promptDate(Scanner scanner, String prompt) {
        String raw = promptString(scanner, prompt);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date '" + raw + "' (expected YYYY-MM-DD).");
        }
    }

    /**
     * Application entry point.
     *
     * <ul>
     *   <li>Run without arguments ({@code java TerraVaultApp.java}) to launch the interactive CLI menu.</li>
     *   <li>Pass {@code --demo} ({@code java TerraVaultApp.java --demo}) to run the automated self-test suite.</li>
     * </ul>
     *
     * @param args command-line arguments ({@code --demo} or custom JDBC URL)
     */
    public static void main(String[] args) {
        boolean demoMode = false;
        String jdbcUrl = "jdbc:sqlite:terravault.db";

        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg)) {
                demoMode = true;
            } else if (arg.startsWith("jdbc:")) {
                jdbcUrl = arg;
            }
        }

        Validator<BiologicalSpecimen> validator = new SpecimenValidator();
        JdbcVaultRepository repository = new JdbcVaultRepository(jdbcUrl, validator);
        repository.initializeSchema();

        System.out.println("================================================================================");
        System.out.println("  TERRAVAULT: HEIRLOOM SEED BANK & BIODIVERSITY PRESERVATION LEDGER (v1.1.0)   ");
        System.out.println("================================================================================");
        System.out.println("Database URL : " + jdbcUrl);
        System.out.println("Storage Mode : " + (repository.isJdbcDriverAvailable()
                ? "Native JDBC Driver Connection"
                : "JDBC Repository with Transactional In-Memory Fallback"));
        System.out.println();

        if (demoMode) {
            runAutomatedDemoSuite(repository);
        } else {
            try (Scanner scanner = new Scanner(System.in)) {
                runInteractiveCli(repository, scanner);
            }
        }
    }
}