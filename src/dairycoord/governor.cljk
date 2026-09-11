(ns dairycoord.governor
  "DairyCoordGovernor — the independent safety/scope layer gating
  every dairy-shop scheduling/logistics proposal an advisor may make
  for a dairy products making crew. The governor never dispatches
  hardware itself, never performs cheese/butter/yogurt processing
  work itself, and never finalizes a pasteurization-clearance
  decision, a food-safety-clearance decision (declaring a batch fit
  for sale), or overrides a shop safety officer's judgment — those
  are permanently out of this actor's scope and remain a shop safety
  officer's exclusive judgment (README's 'Robotics premise': this
  actor coordinates DAIRY-SHOP SCHEDULING/LOGISTICS ONLY — it never
  performs processing work itself). Modeled closely on
  cloud-itonami-isco-7211's foundrycoord.governor (closest domain
  shape — a physical-production coordination governor with the same
  independently-registered-facility HARD checks and the same
  closed-allowlist / scope-excluded-op / scope-excluded-phrase shape).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. maker provenance      — the crew member must be independently
                                verified/registered before any action.
    2. shop provenance        — the dairy-processing shop site must be
                                independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs processing work itself;
                                it only gates what the advisor may
                                coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                pasteurization-clearance decision, a
                                food-safety-clearance decision
                                (declaring a batch fit for sale), or
                                to override a shop safety officer's
                                judgment, is a hard, permanent block
                                (checked both against the proposed
                                :op and, defense-in-depth, against the
                                proposal's :rationale text — matched
                                as full finalization/execution ACTION
                                phrases such as \"declare the batch
                                pasteurized and cleared\" /
                                \"finalize the food-safety clearance\"
                                / \"override the shop safety officer's
                                judgment\", never as bare nouns like
                                \"milk\", \"pasteurization\" or
                                \"safety\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged work
                                record for maker …\" or \"scheduled
                                crew operation for pasteurization-batch
                                task …\" or \"…routed for shop safety
                                officer review\" — all three
                                legitimately contain those bare nouns
                                but none is a finalization action, and
                                all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a contamination-risk / hygiene-
                                compliance / equipment-condition
                                concern always escalates to a human,
                                never auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [dairycoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-pasteurization-clearance
    :declare-batch-pasteurized-and-cleared
    :finalize-food-safety-clearance
    :declare-batch-fit-for-sale
    :override-shop-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("milk", "pasteurization", "safety", "dairy", "officer") — so this
;; can never match inside the mock advisor's own default rationale
;; text (which legitimately contains those bare nouns, e.g.
;; "pasteurization-batch task" / "shop safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["declare the batch pasteurized and cleared"
   "declare the batch fit for sale"
   "clear the batch for sale"
   "finalize the pasteurization clearance"
   "finalize the food-safety clearance"
   "finalize the food safety clearance"
   "override the shop safety officer's judgment"
   "override the safety officer's judgment"
   "override shop safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal maker-record shop-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? maker-record)
      (conj {:rule :no-maker
             :detail "未登録 maker への提案は不可（maker record は独立して検証・登録済みでなければならない）"})

      (nil? shop-record)
      (conj {:rule :no-shop
             :detail "未登録 shop への提案は不可（shop record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は乳製品加工作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "低温殺菌クリアランス・食品安全クリアランスの確定・shop safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `dairycoord.store/Store`. Pure — never mutates
  the store, never dispatches a dairy-shop processing operation."
  [request _context proposal store]
  (let [maker-record (store/maker store (:maker-id request))
        shop-record (some->> (:shop-id proposal) (store/shop store))
        hard (hard-violations proposal maker-record shop-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
