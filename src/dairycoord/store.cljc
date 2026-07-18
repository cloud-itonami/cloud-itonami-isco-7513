(ns dairycoord.store
  "SSoT for the ISCO-08 7513 dairy products makers dairy-shop
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a dairy-shop scheduling/logistics coordination robot
  performs crew scheduling, batch/inventory/progress-record logging
  and dairy-materials supply-order coordination for a dairy products
  making crew under this advisor/governor pair, which never
  dispatches hardware itself, never performs cheese/butter/yogurt
  processing work itself, and never finalizes a
  pasteurization-clearance decision, a food-safety-clearance decision
  or overrides a shop safety officer's judgment — those remain the
  shop safety officer's exclusive judgment). Modeled closely on
  cloud-itonami-isco-7211's foundrycoord.store (closest domain shape
  — a physical-production coordination actor with an independently
  registered facility and a cost-gated supply-order escalation).

  Domain:

    maker   — a registered dairy products making crew member
              (:maker-id, :name)
    shop    — a registered dairy-processing shop site {:shop-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged batch/inventory/
              progress entry, a scheduled crew/processing-schedule
              operation, a flagged safety concern, or a coordinated
              raw-milk/dairy-materials supply order) — written ONLY
              via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (maker [s maker-id])
  (shop [s shop-id])
  (records-of [s maker-id])
  (ledger [s])
  (register-maker! [s m])
  (register-shop! [s sh])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (maker [_ maker-id] (get-in @a [:makers maker-id]))
  (shop [_ shop-id] (get-in @a [:shops shop-id]))
  (records-of [_ maker-id] (filter #(= maker-id (:maker-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-maker! [s m]
    (swap! a assoc-in [:makers (:maker-id m)] m) s)
  (register-shop! [s sh]
    (swap! a assoc-in [:shops (:shop-id sh)] sh) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:makers {} :shops {} :records [] :ledger []}
                                    seed)))))
