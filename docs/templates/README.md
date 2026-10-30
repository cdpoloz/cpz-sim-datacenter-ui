# Filled backend configuration template

[datacenter-configuration-example.xlsx](datacenter-configuration-example.xlsx)
is a filled example of the **backend's initial configuration template**, using
this UI repository's demonstration datacenter.

## Template lineage and source data

This workbook follows template version `0.1-proposed` from
[`cpz-sim-datacenter`, commit `ca94bba`](https://github.com/cdpoloz/cpz-sim-datacenter/tree/ca94bbac2b4c8c2ac6af6a94460571bae6b0062c/docs/templates).
The canonical blank workbook and completion rules are maintained in that backend
repository. The example preserves its 13 worksheets, technical field names,
dictionary, units, input conventions and dropdown categories. All instructions,
worksheet names and dropdown labels are in English.

The values were taken from
[`data/config/datacenter.json` at UI commit `972d389`](https://github.com/cdpoloz/cpz-sim-datacenter-ui/blob/972d389c9e15131e02b4836d18988828d70e3462/data/config/datacenter.json).
They are demonstration values, not measurements or validated client equipment
specifications. Imported rows are marked `REPO_DEMO`. Missing values remain blank;
no physical parameters or client metadata have been invented. In particular,
`health` is absent from that JSON and its four template thresholds remain blank.

## Example contents

| Configuration element | Count |
| --- | ---: |
| Room | 1 |
| Rack columns | 8 |
| Racks | 96 |
| Declared slots, including empty slots | 1,152 |
| Installed servers | 843 |
| Server models | 3 |
| Hot aisles | 5 |
| Room cooling zones | 39 |
| SUPPLY units | 4 |
| EXHAUST units | 5 |
| Unit-to-zone influences | 78 |
| Dedicated rack cooling units | 5 |

## Relationship to runtime configuration

This is a reviewable representation of the existing backend JSON configuration.
The UI still loads `data/config/datacenter.json`; it does **not** import Excel.
The workbook was populated from that JSON. It does not imply that the existing
JSON was originally generated from Excel. Automatic workbook-to-JSON conversion
is not implemented yet.

The mapping separates inventory, reusable server models, explicit rack slots,
cooling-zone lists, unit-to-zone weights and physical model parameters. Auxiliary
fields such as `provenance`, `externalId`, `notes`, configuration version and
effective dates belong to the exchange layer and are not new backend properties.

This workbook does not describe the complete UI setup. Screen coordinates,
control assets, `config.properties` (including UI-configured health settings) and
UI-specific display names or aisle rendering rules remain in their existing
files. See [Configuration and Assets](../configuration.md).

## Validation and maintenance

The saved workbook was checked by reconstructing the source JSON and comparing
all fields and values, including optional blocks, booleans and numeric values.
Identity references and influence weights were checked, and worksheet content
and layout were reviewed. These checks establish representation fidelity; they
do not establish physical calibration.

When the demo JSON changes, refresh the workbook and its source revision together.
When the backend template changes, first review compatibility, preserve its
structure and field meanings, and update the referenced template version and
backend revision. Keep the backend's blank template as the canonical capture
format rather than maintaining a second independent schema in this repository.
