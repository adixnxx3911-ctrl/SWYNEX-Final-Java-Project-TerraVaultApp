# TerraVault: Heirloom Seed Bank & Biodiversity Preservation Ledger

**TerraVault** is a complete, interactive Java 17 application engineered to manage biological conservation specimens across deep-freeze seed vaults, liquid-nitrogen cryobanks, and mycorrhizal spore repositories. It combines **Object-Oriented Programming (OOP)**, **relational JDBC database operations with ACID transactions**, **multi-rule domain validation**, an **interactive console CLI menu**, and **Javadoc API documentation** in a zero-setup package.

## Key Architectural Pillars

| Pillar | Implementation Highlights |
| :--- | :--- |
| **Object-Oriented Programming** | Abstract base class `BiologicalSpecimen` implementing `ViabilityAuditable`, extended by three polymorphic subclasses (`HeirloomSeedLot`, `CryoTissueCulture`, `MycelialSporeCulture`), generic `Validator<T>` strategy interface, and immutable value records (`ValidationResult`, `ViabilityAuditRecord`). |
| **Database Operations (JDBC)** | `JdbcVaultRepository` implementing `VaultRepository` using parameterized `PreparedStatement` CRUD queries, automated DDL schema initialization (`specimens` and `viability_audits` tables), explicit ACID transaction management (`setAutoCommit(false)`, `commit()`, `rollback()`), and `GROUP BY` analytical aggregations. |
| **Validation Framework** | `SpecimenValidator` enforcing accession prefix matching (`^TV-(SD\|CR\|MY)-\d{4}$`), Latin binomial nomenclature (`^[A-Z][a-z]{2,}\s+[a-z]{2,}...`), non-future collection dates, and specimen-specific thermodynamic thresholds before any database write occurs. |
| **Interactive CLI & Demo Mode** | Menu-driven `Scanner(System.in)` console loop (`[1]`–`[9]`, `[0]` to exit) for registering specimens, inspecting audit logs, running transactional chamber updates, and viewing live analytics, plus a `--demo` flag for automated verification. |
| **Documentation** | Complete Javadoc comments (`@param`, `@return`, `@throws`) across every class, enum, interface, constructor, and method, paired with this architectural guide. |

## Interactive CLI Menu Options

Running the application launches an interactive console menu pre-seeded with three reference specimens:

```text
================ TERRAVAULT INTERACTIVE CONSOLE MENU ================
  [1] List all biological specimens in vault
  [2] Register new Heirloom Seed Lot (TV-SD-XXXX)
  [3] Register new Cryogenic Tissue Culture (TV-CR-XXXX)
  [4] Register new Mycelial Spore Culture (TV-MY-XXXX)
  [5] Inspect specimen & audit history by Accession Code
  [6] Update storage temperature & log viability audit (Transactional)
  [7] Delete specimen by Accession Code
  [8] View analytical viability report by category
  [9] Run automated validation & transaction demo suite
  [0] Exit TerraVault
```

## Object-Oriented Class Hierarchy

```text
ViabilityAuditable (Interface)
  └── BiologicalSpecimen (Abstract Class)
        ├── HeirloomSeedLot        (Harrington moisture/temperature longevity decay model)
        ├── CryoTissueCulture      (Vitrification stability & somaclonal passage drift model)
        └── MycelialSporeCulture   (Logarithmic spore density & ecological guild decay model)

Validator<T> (Generic Interface)
  └── SpecimenValidator            (Validates taxonomic, temporal & thermodynamic invariants)

VaultRepository (DAO Interface)
  └── JdbcVaultRepository          (JDBC PreparedStatement CRUD, ACID transactions & SQL analytics)
```

### Polymorphic Viability Formulas

Each concrete subclass overrides `calculateViabilityIndex()` and `getConservationProtocol()` based on its biological storage physiology:

1. **`HeirloomSeedLot`**: Calculates longevity from baseline germination percentage minus cumulative age decay scaled by internal seed moisture content (`> 6.0%` penalty) and chamber temperature (`> -18.0°C` penalty), plus a population reserve factor for lots exceeding 1,500 seeds.
2. **`CryoTissueCulture`**: Computes vitrification stability starting at `97.5%`, applying somaclonal variation penalties per in-vitro subculture passage (`1.65%` per passage beyond passage 1), thermal drift penalties if stored warmer than `-180.0°C`, and liquid nitrogen reserve penalties if dewar fill drops below `60.0%`.
3. **`MycelialSporeCulture`**: Evaluates logarithmic spore concentration (`log10(millionSporesPerMl)`), adjusts for symbiotic guild sensitivity (`MYCORRHIZAL` vs. `SAPROPHYTIC` / `ENDOPHYTIC`), and subtracts annual thermal decay.

## Relational Database Schema (JDBC)

`JdbcVaultRepository#initializeSchema()` automatically provisions two relational tables with foreign-key cascade integrity:

| Table | Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `specimens` | `id` | `VARCHAR(64)` | `PRIMARY KEY` | UUID v4 specimen identifier |
| `specimens` | `accession_code` | `VARCHAR(32)` | `NOT NULL UNIQUE` | Vault code (`TV-SD-XXXX`, `TV-CR-XXXX`, `TV-MY-XXXX`) |
| `specimens` | `specimen_type` | `VARCHAR(32)` | `NOT NULL` | Enum discriminator (`HEIRLOOM_SEED`, `CRYO_TISSUE`, `MYCELIAL_SPORE`) |
| `specimens` | `scientific_name` | `VARCHAR(128)` | `NOT NULL` | Latin binomial species name |
| `specimens` | `common_name` | `VARCHAR(128)` | `NOT NULL` | Vernacular or cultivar name |
| `specimens` | `origin_region` | `VARCHAR(128)` | `NOT NULL` | Provenance bioregion |
| `specimens` | `collection_date` | `VARCHAR(32)` | `NOT NULL` | ISO-8601 harvest/isolation date |
| `specimens` | `storage_temp_c` | `DOUBLE PRECISION` | `NOT NULL` | Active storage temperature in Celsius |
| `specimens` | `viability_index` | `DOUBLE PRECISION` | `NOT NULL` | Polymorphically computed viability score (`0.0`–`100.0`) |
| `specimens` | `attributes_summary` | `VARCHAR(512)` | `NOT NULL` | Subclass-specific biological metadata |
| `viability_audits` | `audit_id` | `VARCHAR(64)` | `PRIMARY KEY` | UUID v4 audit event identifier |
| `viability_audits` | `accession_code` | `VARCHAR(32)` | `FOREIGN KEY` | References `specimens(accession_code) ON DELETE CASCADE` |
| `viability_audits` | `auditor_name` | `VARCHAR(128)` | `NOT NULL` | Inspecting botanist or cryo-technician |
| `viability_audits` | `measured_viability` | `DOUBLE PRECISION` | `NOT NULL` | Viability score recorded at inspection |
| `viability_audits` | `health_status` | `VARCHAR(64)` | `NOT NULL` | `OPTIMAL`, `MONITOR`, or `CRITICAL_REGENERATION_REQUIRED` |
| `viability_audits` | `notes` | `VARCHAR(512)` | `NULLABLE` | Inspection observations and chamber notes |
| `viability_audits` | `audited_at` | `TIMESTAMP` | `NOT NULL` | UTC timestamp of audit execution |

## Domain Validation Rules

`SpecimenValidator` aggregates all constraint violations into an immutable `ValidationResult` before throwing a `ValidationException`:

| Rule Category | Validation Constraint | Failure Example |
| :--- | :--- | :--- |
| **Accession Code Format** | Must match `^TV-(SD\|CR\|MY)-\d{4}$` and match the subclass `SpecimenType` prefix | `TV-SD-9999` assigned to a `CryoTissueCulture` (`expected prefix 'TV-CR-'`) |
| **Binomial Nomenclature** | Must match `^[A-Z][a-z]{2,}\s+[a-z]{2,}(\s+(var\.\|subsp\.)\s+[a-z]{2,})?$` | `invalid_binomial_format` |
| **Collection Date** | Must not be null, in the future, or earlier than `1900-01-01` | `2026-11-15` (future date) |
| **Orthodox Seed Lot** | Storage temperature in `[-30.0°C, +5.0°C]`, moisture in `[2.0%, 9.0%]`, germination in `[0%, 100%]`, `seedCount > 0` | Moisture at `14.5%` or temperature at `+18.0°C` |
| **Cryo Tissue Culture** | Storage temperature in `[-200.0°C, -130.0°C]`, LN2 level in `[25.0%, 100.0%]`, passage number in `[1, 25]`, non-blank tank ID | Temperature at `-40.0°C` (above glass transition) or LN2 at `12.0%` |
| **Mycelial Spore Culture** | Storage temperature in `[-85.0°C, +8.0°C]`, spore density in `(0.0, 1000.0] M/mL`, non-blank substrate | Spore density `<= 0.0 M/mL` |

## Project Structure

```text
terravault-seed-bank/
├── pom.xml                                      # Maven build configuration (Java 17 + SQLite JDBC + Javadoc plugin)
├── README.md                                    # Architectural & operational documentation
└── src/
    └── main/
        └── java/
            └── com/
                └── terravault/
                    └── TerraVaultApp.java       # Complete interactive application source with Javadocs
```

## Build, Run & Documentation Commands

### 1. Run Interactive CLI Mode

```bash
# Using Maven (with SQLite JDBC persistence)
mvn clean compile exec:java -Dexec.mainClass="com.terravault.TerraVaultApp"

# Or directly with JDK 17+ single-file source execution
java src/main/java/com/terravault/TerraVaultApp.java
```

### 2. Run Automated Demo & Validation Suite (`--demo`)

```bash
java src/main/java/com/terravault/TerraVaultApp.java --demo
```

### 3. Generate HTML Javadoc Documentation

```bash
javadoc -d docs/javadoc -sourcepath src/main/java -subpackages com.terravault
```
