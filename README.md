# cloud-itonami-isco-7513

Open Occupation Blueprint for **ISCO-08 7513**: Dairy Products Makers.

This repository designs a forkable OSS business for a dairy-shop scheduling and logistics coordination practice: a dairy-shop scheduling and supply-coordination robot manages crew/task records under a governor-gated actor, so a dairy products making crew keeps its own operating records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/dairycoord/` implements the
`DairyCoordActor` as a `langgraph.graph/state-graph`
(`dairycoord.actor`) wired to a `Dairy Coordination Advisor`
(`dairycoord.advisor`) and an independent `DairyCoordGovernor`
(`dairycoord.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 22 tests / 47 assertions green (`clojure -M:test`).
HARD invariants (always hold, never overridable): maker provenance,
dairy-shop provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a pasteurization-clearance
decision, a food-safety-clearance decision (declaring a batch fit for
sale), or override a shop safety officer's judgment. Always-escalate
paths (human sign-off regardless of confidence, mapping this repo's
Trust Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a dairy-shop scheduling/logistics coordination robot performs crew scheduling, batch/inventory/progress-record logging and raw-milk/dairy-materials supply-order coordination for a dairy products making crew, under an actor that proposes actions and an independent **Dairy Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs cheese/butter/yogurt processing work on the shop floor, and never finalizes a pasteurization-clearance decision, a food-safety-clearance decision, or overrides a shop safety officer's judgment; `:high`/`:safety-critical` actions (such as a flagged contamination-risk/hygiene-compliance/equipment-condition concern, or an above-threshold supply order) require human sign-off. **This actor coordinates dairy-shop scheduling/logistics only — it never performs processing work or makes pasteurization/food-safety-clearance decisions itself.**

## Core Contract

```text
crew roster + dairy-shop registration + safety-reporting policy
        |
        v
Dairy Coordination Advisor -> Dairy Coordination Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a pasteurization-clearance decision, finalize a food-safety-clearance
decision, override a shop safety officer's judgment, suppress an
operating record, or disclose sensitive data without governor approval
and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7513`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
