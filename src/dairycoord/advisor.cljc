(ns dairycoord.advisor
  "Dairy Coordination Advisor — proposing a dairy-shop
  scheduling/logistics coordination operation (log a work record,
  schedule a crew operation, flag a safety concern, coordinate a
  raw-milk/dairy-materials supply order) from a crew roster, dairy-shop
  registration and safety-reporting policy. Swappable mock/llm; the
  advisor ONLY proposes — `dairycoord.governor` independently gates
  every proposal and always escalates safety concerns and
  above-threshold supply orders. The advisor never proposes to
  directly finalize a pasteurization-clearance decision, a
  food-safety-clearance decision (declaring a batch fit for sale), or
  to override a shop safety officer's judgment — those stay
  permanently out of this actor's scope. Modeled closely on
  cloud-itonami-isco-7211's advisor (closest domain shape — a
  physical-production coordination advisor with the same closed
  four-op proposal shape).

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :maker-id str :shop-id str
               :cost number :hazard-type kw :task str :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op maker-id shop-id hazard-type]
  (case op
    :log-work-record
    (str "logged work record for maker " maker-id " at shop " shop-id)

    :schedule-crew-operation
    (str "scheduled crew operation for pasteurization-batch task at shop " shop-id)

    :flag-safety-concern
    (str "flagged " (name (or hazard-type :hazard)) " concern for maker "
         maker-id " at shop " shop-id " — routed for shop safety officer review")

    :coordinate-supply-order
    (str "coordinated supply order for maker " maker-id " at shop " shop-id)

    (str "proposed " (name op) " for maker " maker-id " at shop " shop-id)))

(defn- infer [_store {:keys [op stake maker-id shop-id cost hazard-type task]
                       :as request}]
  {:op op
   :effect :propose
   :maker-id maker-id
   :shop-id shop-id
   :cost cost
   :hazard-type hazard-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op maker-id shop-id hazard-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a dairy products makers dairy-shop
   scheduling/logistics coordination advisor. Given a request,
   propose an :op (one of :log-work-record, :schedule-crew-operation,
   :flag-safety-concern, :coordinate-supply-order), the :maker-id,
   :shop-id, and any :cost/:hazard-type/:task fields, an honest
   :confidence and a :stake. Never propose an op outside this closed
   list, and never propose to directly finalize a
   pasteurization-clearance decision, a food-safety-clearance decision
   (declaring a batch fit for sale), or to override a shop safety
   officer's judgment — those are always out of this actor's scope; it
   coordinates dairy-shop scheduling/logistics only and never performs
   processing work or makes pasteurization/food-safety-clearance
   decisions itself. Safety concerns always require human sign-off
   regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
