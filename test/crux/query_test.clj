(ns crux.query-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [crux.fixtures :as f :refer [*kv*]]
            [crux.query :as q]))

(t/use-fixtures :each f/with-kv-store)

(t/deftest test-sanity-check
  (f/transact-people! *kv* [{:name "Ivan"}])
  (t/is (first (q/q (q/db *kv*) '{:find [e]
                                  :where [[e :name "Ivan"]]}))))

(t/deftest test-basic-query
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov"}])

  (t/testing "Can query value by single field"
    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [name]
                                            :where [[e :name "Ivan"]
                                                    [e :name name]]})))
    (t/is (= #{["Petr"]} (q/q (q/db *kv*) '{:find [name]
                                            :where [[e :name "Petr"]
                                                    [e :name name]]}))))

  (t/testing "Can query entity by single field"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [e]
                                           :where [[e :name "Ivan"]]})))
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [e]
                                           :where [[e :name "Petr"]]}))))

  (t/testing "Can query using multiple terms"
    (t/is (= #{["Ivan" "Ivanov"]} (q/q (q/db *kv*) '{:find [name last-name]
                                                     :where [[e :name name]
                                                             [e :last-name last-name]
                                                             [e :name "Ivan"]
                                                             [e :last-name "Ivanov"]]}))))

  (t/testing "Negate query based on subsequent non-matching clause"
    (t/is (= #{} (q/q (q/db *kv*) '{:find [e]
                                    :where [[e :name "Ivan"]
                                            [e :last-name "Ivanov-does-not-match"]]}))))

  (t/testing "Can query for multiple results"
    (t/is (= #{["Ivan"] ["Petr"]}
             (q/q (q/db *kv*) '{:find [name] :where [[e :name name]]}))))


  (f/transact-people! *kv* [{:crux.db/id :smith :name "Smith" :last-name "Smith"}])
  (t/testing "Can query across fields for same value"
    (t/is (= #{[:smith]}
             (q/q (q/db *kv*) '{:find [p1] :where [[p1 :name name]
                                                   [p1 :last-name name]]}))))

  (t/testing "Can query across fields for same value when value is passed in"
    (t/is (= #{[:smith]}
             (q/q (q/db *kv*) '{:find [p1] :where [[p1 :name name]
                                                   [p1 :last-name name]
                                                   [p1 :name "Smith"]]})))))

(t/deftest test-query-with-arguments
  (let [[ivan petr] (f/transact-people! *kv* [{:name "Ivan" :last-name "Ivanov"}
                                              {:name "Petr" :last-name "Petrov"}])]

    (t/testing "Can query entity by single field"
      (t/is (= #{[(:crux.db/id ivan)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]]
                                                          :args [{:name "Ivan"}]})))
      (t/is (= #{[(:crux.db/id petr)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]]
                                                          :args [{:name "Petr"}]}))))

    (t/testing "Can query entity by entity position"
      (t/is (= #{["Ivan"]
                 ["Petr"]} (q/q (q/db *kv*) {:find '[name]
                                             :where '[[e :name name]]
                                             :args [{:e (:crux.db/id ivan)}
                                                    {:e (:crux.db/id petr)}]})))

      (t/is (= #{["Ivan" "Ivanov"]
                 ["Petr" "Petrov"]} (q/q (q/db *kv*) {:find '[name last-name]
                                                      :where '[[e :name name]
                                                               [e :last-name last-name]]
                                                      :args [{:e (:crux.db/id ivan)}
                                                             {:e (:crux.db/id petr)}]}))))

    (t/testing "Can match on both entity and value position"
      (t/is (= #{["Ivan"]} (q/q (q/db *kv*) {:find '[name]
                                             :where '[[e :name name]]
                                             :args [{:e (:crux.db/id ivan)
                                                     :name "Ivan"}]})))

      (t/is (= #{} (q/q (q/db *kv*) {:find '[name]
                                     :where '[[e :name name]]
                                     :args [{:e (:crux.db/id ivan)
                                             :name "Petr"}]}))))

    (t/testing "Can query entity by single field with several arguments"
      (t/is (= #{[(:crux.db/id ivan)]
                 [(:crux.db/id petr)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]]
                                                          :args [{:name "Ivan"}
                                                                 {:name "Petr"}]}))))

    (t/testing "Can query entity by single field with literals"
      (t/is (= #{[(:crux.db/id ivan)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]
                                                                  [e :last-name "Ivanov"]]
                                                          :args [{:name "Ivan"}
                                                                 {:name "Petr"}]}))))

    (t/testing "Can query entity by non existent argument"
      (t/is (= #{} (q/q (q/db *kv*) '{:find [e]
                                      :where [[e :name name]]
                                      :args [{:name "Bob"}]}))))

    (t/testing "Can query entity with empty arguments"
      (t/is (= #{[(:crux.db/id ivan)]
                 [(:crux.db/id petr)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]]
                                                          :args []}))))

    (t/testing "Can query entity with tuple arguments"
      (t/is (= #{[(:crux.db/id ivan)]
                 [(:crux.db/id petr)]} (q/q (q/db *kv*) '{:find [e]
                                                          :where [[e :name name]
                                                                  [e :last-name last-name]]
                                                          :args [{:name "Ivan" :last-name "Ivanov"}
                                                                 {:name "Petr" :last-name "Petrov"}]}))))

    (t/testing "Can query predicates based on arguments alone"
      (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [name]
                                              :where [[(re-find #"I" name)]]
                                              :args [{:name "Ivan"}
                                                     {:name "Petr"}]})))

      (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [name]
                                              :where [[(re-find #"I" name)]
                                                      [(= last-name "Ivanov")]]
                                              :args [{:name "Ivan" :last-name "Ivanov"}
                                                     {:name "Petr" :last-name "Petrov"}]})))

      (t/is (= #{["Ivan"]
                 ["Petr"]} (q/q (q/db *kv*) '{:find [name]
                                              :where [[(string? name)]]
                                              :args [{:name "Ivan"}
                                                     {:name "Petr"}]})))

      (t/is (= #{["Ivan" "Ivanov"]
                 ["Petr" "Petrov"]} (q/q (q/db *kv*) '{:find [name
                                                              last-name]
                                                       :where [[(not= last-name name)]]
                                                       :args [{:name "Ivan" :last-name "Ivanov"}
                                                              {:name "Petr" :last-name "Petrov"}]})))

      (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [name]
                                              :where [[(string? name)]
                                                      [(re-find #"I" name)]]
                                              :args [{:name "Ivan"}
                                                     {:name "Petr"}]})))

      (t/is (= #{} (q/q (q/db *kv*) '{:find [name]
                                      :where [[(number? name)]]
                                      :args [{:name "Ivan"}
                                             {:name "Petr"}]})))

      (t/is (= #{} (q/q (q/db *kv*) '{:find [name]
                                      :where [(not [(string? name)])]
                                      :args [{:name "Ivan"}
                                             {:name "Petr"}]}))))))

(t/deftest test-multiple-results
  (f/transact-people! *kv* [{:name "Ivan" :last-name "1"}
                            {:name "Ivan" :last-name "2"}])
  (t/is (= 2
           (count (q/q (q/db *kv*) '{:find [e] :where [[e :name "Ivan"]]})))))

(t/deftest test-query-using-keywords
  (f/transact-people! *kv* [{:name "Ivan" :sex :male}
                            {:name "Petr" :sex :male}
                            {:name "Doris" :sex :female}
                            {:name "Jane" :sex :female}])

  (t/testing "Can query by single field"
    (t/is (= #{["Ivan"] ["Petr"]} (q/q (q/db *kv*) '{:find [name]
                                                     :where [[e :name name]
                                                             [e :sex :male]]})))
    (t/is (= #{["Doris"] ["Jane"]} (q/q (q/db *kv*) '{:find [name]
                                                      :where [[e :name name]
                                                              [e :sex :female]]})))))

(t/deftest test-basic-query-at-t
  (let [[malcolm] (f/transact-people! *kv* [{:crux.db/id :malcolm :name "Malcolm" :last-name "Sparks"}]
                                      #inst "1986-10-22")]
    (f/transact-people! *kv* [{:crux.db/id :malcolm :name "Malcolma" :last-name "Sparks"}] #inst "1986-10-24")
    (let [q '{:find [e]
              :where [[e :name "Malcolma"]
                      [e :last-name "Sparks"]]}]
      (t/is (= #{} (q/q (q/db *kv* #inst "1986-10-23")
                        q)))
      (t/is (= #{[(:crux.db/id malcolm)]} (q/q (q/db *kv*) q))))))

(t/deftest test-query-across-entities-using-join
  ;; Five people, two of which share the same name:
  (f/transact-people! *kv* [{:name "Ivan"} {:name "Petr"} {:name "Sergei"} {:name "Denis"} {:name "Denis"}])

  (t/testing "Five people, without a join"
    (t/is (= 5 (count (q/q (q/db *kv*) '{:find [p1]
                                         :where [[p1 :name name]
                                                 [p1 :age age]
                                                 [p1 :salary salary]]})))))

  (t/testing "Five people, a cartesian product - joining without unification"
    (t/is (= 25 (count (q/q (q/db *kv*) '{:find [p1 p2]
                                          :where [[p1 :name]
                                                  [p2 :name]]})))))

  (t/testing "A single first result, joined to all possible subsequent results in next term"
    (t/is (= 5 (count (q/q (q/db *kv*) '{:find [p1 p2]
                                         :where [[p1 :name "Ivan"]
                                                 [p2 :name]]})))))

  (t/testing "A single first result, with no subsequent results in next term"
    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [p1]
                                         :where [[p1 :name "Ivan"]
                                                 [p2 :name "does-not-match"]]})))))

  (t/testing "Every person joins once, plus 2 more matches"
    (t/is (= 7 (count (q/q (q/db *kv*) '{:find [p1 p2]
                                         :where [[p1 :name name]
                                                 [p2 :name name]]}))))))

(t/deftest test-join-over-two-attributes
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :petr :name "Petr" :follows #{"Ivanov"}}])

  (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [e2]
                                         :where [[e :last-name last-name]
                                                 [e2 :follows last-name]
                                                 [e :name "Ivan"]]}))))

(t/deftest test-blanks
  (f/transact-people! *kv* [{:name "Ivan"} {:name "Petr"} {:name "Sergei"}])

  (t/is (= #{["Ivan"] ["Petr"] ["Sergei"]}
           (q/q (q/db *kv*) '{:find [name]
                              :where [[_ :name name]]}))))

(t/deftest test-exceptions
  (t/testing "Unbound query variable"
    (try
      (q/q (q/db *kv*) '{:find [bah]
                         :where [[e :name]]})
      (t/is (= true false) "Expected exception")
      (catch IllegalArgumentException e
        (t/is (= "Find refers to unknown variable: bah" (.getMessage e)))))

    (try
      (q/q (q/db *kv*) '{:find [x]
                         :where [[x :foo]
                                 [(+ 1 bah)]]})
      (t/is (= true false) "Expected exception")
      (catch IllegalArgumentException e
        (t/is (re-find #"Predicate refers to unknown variable: bah" (.getMessage e)))))))

(t/deftest test-not-query
  (t/is (= '[[:bgp {:e e :a :name :v name}]
             [:bgp {:e e :a :name :v "Ivan"}]
             [:not [[:bgp {:e e :a :last-name :v "Ivannotov"}]]]]

           (s/conform :crux.query/where '[[e :name name]
                                          [e :name "Ivan"]
                                          (not [e :last-name "Ivannotov"])])))

  (f/transact-people! *kv* [{:crux.db/id :ivan-ivanov-1 :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :ivan-ivanov-2 :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :ivan-ivanovtov-1 :name "Ivan" :last-name "Ivannotov"}])

  (t/testing "literal v"
    (t/is (= 1 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [e :name "Ivan"]
                                                 (not [e :last-name "Ivanov"])]}))))

    (t/is (= 1 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 (not [e :last-name "Ivanov"])]}))))

    (t/is (= 1 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name "Ivan"]
                                                 (not [e :last-name "Ivanov"])]}))))

    (t/is (= 2 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [e :name "Ivan"]
                                                 (not [e :last-name "Ivannotov"])]})))))

  (t/testing "variable v"
    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [e :name "Ivan"]
                                                 (not [e :name name])]}))))

    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 (not [e :name name])]}))))

    (t/is (= 2 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [:ivan-ivanovtov-1 :last-name i-name]
                                                 (not [e :last-name i-name])]})))))

  (t/testing "literal entities"
    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 (not [:ivan-ivanov-1 :name name])]}))))

    (t/is (= 1 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :last-name last-name]
                                                 (not [:ivan-ivanov-1 :last-name last-name])]}))))))

(t/deftest test-or-query
  (f/transact-people! *kv* [{:name "Ivan" :last-name "Ivanov"}
                            {:name "Ivan" :last-name "Ivanov"}
                            {:name "Ivan" :last-name "Ivannotov"}
                            {:name "Bob" :last-name "Controlguy"}])

  ;; Here for dev reasons, delete when appropiate
  (t/is (= '[[:bgp {:e e :a :name :v name}]
             [:bgp {:e e :a :name :v "Ivan"}]
             [:or [[:term [:bgp {:e e :a :last-name :v "Ivanov"}]]]]]
           (s/conform :crux.query/where '[[e :name name]
                                          [e :name "Ivan"]
                                          (or [e :last-name "Ivanov"])])))

  (t/testing "Or works as expected"
    (t/is (= 3 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [e :name "Ivan"]
                                                 (or [e :last-name "Ivanov"]
                                                     [e :last-name "Ivannotov"])]}))))

    (t/is (= 4 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [(or [e :last-name "Ivanov"]
                                                     [e :last-name "Ivannotov"]
                                                     [e :last-name "Controlguy"])]}))))

    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [(or [e :last-name "Controlguy"])
                                                 (or [e :last-name "Ivanov"]
                                                     [e :last-name "Ivannotov"])]}))))


    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [(or [e :last-name "Ivanov"])
                                                 (or [e :last-name "Ivannotov"])]}))))

    (t/is (= 0 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :last-name "Controlguy"]
                                                 (or [e :last-name "Ivanov"]
                                                     [e :last-name "Ivannotov"])]}))))

    (t/is (= 3 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 (or [e :last-name "Ivanov"]
                                                     [e :name "Bob"])]})))))

  (t/testing "Or edge case - can take a single clause"
    ;; Unsure of the utility
    (t/is (= 2 (count (q/q (q/db *kv*) '{:find [e]
                                         :where [[e :name name]
                                                 [e :name "Ivan"]
                                                 (or [e :last-name "Ivanov"])]}))))))

(t/deftest test-or-query-can-use-and
  (let [[ivan] (f/transact-people! *kv* [{:name "Ivan" :sex :male}
                                         {:name "Bob" :sex :male}
                                         {:name "Ivana" :sex :female}])]

    (t/is (= #{["Ivan"]
               ["Ivana"]}
             (q/q (q/db *kv*) '{:find [name]
                                :where [[e :name name]
                                        (or [e :sex :female]
                                            (and [e :sex :male]
                                                 [e :name "Ivan"]))]})))

    (t/is (= #{[(:crux.db/id ivan)]}
             (q/q (q/db *kv*) '{:find [e]
                                :where [(or [e :name "Ivan"])]})))

    (t/is (= #{}
             (q/q (q/db *kv*) '{:find [name]
                                :where [[e :name name]
                                        (or (and [e :sex :female]
                                                 [e :name "Ivan"]))]})))))

(t/deftest test-ors-must-use-same-vars
  (try
    (q/q (q/db *kv*) '{:find [e]
                       :where [[e :name name]
                               (or [e1 :last-name "Ivanov"]
                                   [e2 :last-name "Ivanov"])]})
    (t/is (= true false) "Expected assertion error")
    (catch IllegalArgumentException e
      (t/is (re-find #"Or requires same logic variables"
                     (.getMessage e))))))

(t/deftest test-ors-can-introduce-new-bindings
  (let [[petr ivan ivanova] (f/transact-people! *kv* [{:name "Petr" :last-name "Smith" :sex :male}
                                                      {:name "Ivan" :last-name "Ivanov" :sex :male}
                                                      {:name "Ivanova" :last-name "Ivanov" :sex :female}])]

    (t/testing "?p2 introduced only inside of an Or"
      (t/is (= #{[(:crux.db/id ivan)]} (q/q (q/db *kv*) '{:find [?p2]
                                                          :where [(or (and [?p2 :name "Petr"]
                                                                           [?p2 :sex :female])
                                                                      (and [?p2 :last-name "Ivanov"]
                                                                           [?p2 :sex :male]))]}))))))

;; TODO: lacks not-join support - might not be supported.
#_(t/deftest test-not-join
    (f/transact-people! *kv* [{:name "Ivan" :last-name "Ivanov"}
                              {:name "Malcolm" :last-name "Ofsparks"}
                              {:name "Dominic" :last-name "Monroe"}])

    (t/testing "Rudimentary or-join"
      (t/is (= #{["Ivan"] ["Malcolm"]}
               (q/q (q/db *kv*) '{:find [name]
                                  :where [[e :name name]
                                          (not-join [e]
                                                    [e :last-name "Monroe"])]})))))

(t/deftest test-mixing-expressions
  (f/transact-people! *kv* [{:name "Ivan" :last-name "Ivanov"}
                            {:name "Derek" :last-name "Ivanov"}
                            {:name "Bob" :last-name "Ivannotov"}
                            {:name "Fred" :last-name "Ivannotov"}])

  ;; TODO: should work.
  #_(t/testing "Or can use not expression"
      (t/is (= #{["Ivan"] ["Derek"] ["Fred"]}
               (q/q (q/db *kv*) '{:find [name]
                                  :where [[e :name name]
                                          (or [e :last-name "Ivanov"]
                                              (not [e :name "Bob"]))]}))))

  (t/testing "Not can use Or expression"
    (t/is (= #{["Fred"]} (q/q (q/db *kv*) '{:find [name]
                                            :where [[e :name name]
                                                    (not (or [e :last-name "Ivanov"]
                                                             [e :name "Bob"]))]})))))

(t/deftest test-predicate-expression
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov" :age 30}
                            {:crux.db/id :bob :name "Bob" :last-name "Ivanov" :age 40}
                            {:crux.db/id :dominic :name "Dominic" :last-name "Monroe" :age 50}])

  (t/testing "range expressions"
    (t/is (= #{["Ivan"] ["Bob"]}
             (q/q (q/db *kv*) '{:find [name]
                                :where [[e :name name]
                                        [e :age age]
                                        [(< age 50)]]})))

    (t/is (= #{["Dominic"]}
             (q/q (q/db *kv*) '{:find [name]
                                :where [[e :name name]
                                        [e :age age]
                                        [(>= age 50)]]})))

    (t/testing "fallback to built in predicate for vars"
      (t/is (= #{["Ivan" 30 "Ivan" 30]
                 ["Ivan" 30 "Bob" 40]
                 ["Ivan" 30 "Dominic" 50]
                 ["Bob" 40 "Bob" 40]
                 ["Bob" 40 "Dominic" 50]
                 ["Dominic" 50 "Dominic" 50]}
               (q/q (q/db *kv*) '{:find [name age1 name2 age2]
                                  :where [[e :name name]
                                          [e :age age1]
                                          [e2 :name name2]
                                          [e2 :age age2]
                                          [(<= age1 age2)]]})))

      (t/is (= #{["Ivan" "Dominic"]
                 ["Ivan" "Bob"]
                 ["Dominic" "Bob"]}
               (q/q (q/db *kv*) '{:find [name1 name2]
                                  :where [[e :name name1]
                                          [e2 :name name2]
                                          [(> name1 name2)]]})))))

  (t/testing "clojure.core predicate"
    (t/is (= #{["Bob"] ["Dominic"]}
             (q/q (q/db *kv*) '{:find [name]
                                :where [[e :name name]
                                        [(re-find #"o" name)]]})))

    (t/testing "No results"
      (t/is (empty? (q/q (q/db *kv*) '{:find [name]
                                       :where [[e :name name]
                                               [(re-find #"X" name)]]}))))

    (t/testing "Not predicate"
      (t/is (= #{["Ivan"]}
               (q/q (q/db *kv*) '{:find [name]
                                  :where [[e :name name]
                                          (not [(re-find #"o" name)])]}))))

    (t/testing "Entity variable"
      (t/is (= #{["Ivan"]}
               (q/q (q/db *kv*) '{:find [name]
                                  :where [[e :name name]
                                          [(= :ivan e)]]})))

      (t/testing "Filtered by value"
        (t/is (= #{[:bob] [:ivan]}
                 (q/q (q/db *kv*) '{:find [e]
                                    :where [[e :last-name last-name]
                                            [(= "Ivanov" last-name)]]})))

        (t/is (= #{[:ivan]}
                 (q/q (q/db *kv*) '{:find [e]
                                    :where [[e :last-name last-name]
                                            [e :age age]
                                            [(= "Ivanov" last-name)]
                                            [(= 30 age)]]})))))

    (t/testing "Several variables"
      (t/is (= #{["Bob"]}
               (q/q (q/db *kv*) '{:find [name]
                                  :where [[e :name name]
                                          [e :age age]
                                          [(= 40 age)]
                                          [(re-find #"o" name)]
                                          [(not= age name)]]})))

      (t/is (= #{[:bob "Ivanov"]}
               (q/q (q/db *kv*) '{:find [e last-name]
                                  :where [[e :last-name last-name]
                                          [e :age age]
                                          [(re-find #"ov$" last-name)]
                                          (not [(= age 30)])]})))

      (t/testing "No results"
        (t/is (= #{}
                 (q/q (q/db *kv*) '{:find [name]
                                    :where [[e :name name]
                                            [e :age age]
                                            [(re-find #"o" name)]
                                            [(= age name)]]})))))))

(t/deftest test-attributes-with-multiple-values
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov" :age 30 :friends #{:bob :dominic}}
                            {:crux.db/id :bob :name "Bob" :last-name "Ivanov" :age 40 :friends #{:ivan :dominic}}
                            {:crux.db/id :dominic :name "Dominic" :last-name "Monroe" :age 50 :friends #{:bob}}])

  (t/testing "can find multiple values"
    (t/is (= #{[:bob] [:dominic]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]]}))))

  (t/testing "can find based on single value"
    (t/is (= #{[:ivan]}
             (q/q (q/db *kv*) '{:find [i]
                                :where [[i :name "Ivan"]
                                        [i :friends :bob]]}))))

  (t/testing "join intersects values"
    (t/is (= #{[:bob]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]
                                        [d :name "Dominic"]
                                        [d :friends f]]}))))

  (t/testing "clojure.core predicate filters values"
    (t/is (= #{[:bob]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]
                                        [(= f :bob)]]}))))

  (t/testing "unification filters values"
    (t/is (= #{[:bob]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]
                                        [(== f :bob)]]})))

    (t/is (= #{[:bob] [:dominic]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]
                                        [(== f #{:bob :dominic})]]})))

    (t/is (= #{[:dominic]}
             (q/q (q/db *kv*) '{:find [f]
                                :where [[i :name "Ivan"]
                                        [i :friends f]
                                        [(!= f :bob)]]})))))

(t/deftest test-can-use-idents-as-entities
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov" :mentor :ivan}])

  (t/testing "Can query by single field"
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [p]
                                           :where [[i :name "Ivan"]
                                                   [p :mentor i]]})))

    (t/testing "Other direction"
      (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [p]
                                             :where [[p :mentor i]
                                                     [i :name "Ivan"]]})))))

  (t/testing "Can query by known entity"
    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[:ivan :name n]]})))

    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[:petr :mentor i]
                                                    [i :name n]]})))

    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[p :name "Petr"]
                                                    [p :mentor i]
                                                    [i :name n]]})))

    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[p :mentor i]
                                                    [i :name n]]})))

    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[p :name "Petr"]
                                                   [p :mentor i]]})))

    (t/testing "Other direction"
      (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                              :where [[i :name n]
                                                      [:petr :mentor i]]}))))
    (t/testing "No matches"
      (t/is (= #{} (q/q (q/db *kv*) '{:find [n]
                                      :where [[:ivan :mentor x]
                                              [x :name n]]})))

      (t/testing "Other direction"
        (t/is (= #{} (q/q (q/db *kv*) '{:find [n]
                                        :where [[x :name n]
                                                [:ivan :mentor x]]})))))))

(t/deftest test-join-and-seek-bugs
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov" :mentor :ivan}])

  (t/testing "index seek bugs"
    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[p :name "Petrov"]
                                            [p :mentor i]]})))


    (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                    :where [[p :name "Pet"]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                    :where [[p :name "I"]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                    :where [[p :name "Petrov"]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[p :name "Pet"]
                                            [p :mentor i]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[p :name "Petrov"]
                                            [p :mentor i]]}))))

  (t/testing "join bugs"
    (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                    :where [[p :name "Ivan"]
                                            [p :mentor i]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[p :name "Ivan"]
                                            [p :mentor i]]})))))

(t/deftest test-queries-with-variables-only
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :mentor :petr}
                            {:crux.db/id :petr :name "Petr" :mentor :oleg}
                            {:crux.db/id :oleg :name "Oleg" :mentor :ivan}])

  (t/is (= #{[:oleg "Oleg" :petr "Petr"]
             [:ivan "Ivan" :oleg "Oleg"]
             [:petr "Petr" :ivan "Ivan"]} (q/q (q/db *kv*) '{:find [e1 n1 e2 n2]
                                                             :where [[e1 :name n1]
                                                                     [e2 :mentor e1]
                                                                     [e2 :name n2]]}))))

(t/deftest test-index-unification
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov" :mentor :ivan}])

  (t/is (= #{[:petr :petr]} (q/q (q/db *kv*) '{:find [p1 p2]
                                               :where [[p1 :name "Petr"]
                                                       [p2 :mentor i]
                                                       [(== p1 p2)]]})))

  (t/is (= #{} (q/q (q/db *kv*) '{:find [p1 p2]
                                  :where [[p1 :name "Petr"]
                                          [p2 :mentor i]
                                          [(== p1 i)]]})))

  (t/is (= #{} (q/q (q/db *kv*) '{:find [p1 p2]
                                  :where [[p1 :name "Petr"]
                                          [p2 :mentor i]
                                          [(== p1 i)]]})))

  (t/is (= #{[:petr :petr]} (q/q (q/db *kv*) '{:find [p1 p2]
                                               :where [[p1 :name "Petr"]
                                                       [p2 :mentor i]
                                                       [(!= p1 i)]]})))

  (t/is (= #{} (q/q (q/db *kv*) '{:find [p1 p2]
                                  :where [[p1 :name "Petr"]
                                          [p2 :mentor i]
                                          [(!= p1 p2)]]})))


  (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                  :where [[p :name "Petr"]
                                          [p :mentor i]
                                          [(== p i)]]})))

  (t/testing "unify with literal"
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [p]
                                           :where [[p :name n]
                                                   [(== n "Petr")]]})))

    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [p]
                                           :where [[p :name n]
                                                   [(!= n "Petr")]]}))))

  (t/testing "unify with entity"
    (t/is (= #{["Petr"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[p :name n]
                                                    [(== p :petr)]]})))

    (t/is (= #{["Ivan"]} (q/q (q/db *kv*) '{:find [n]
                                            :where [[i :name n]
                                                    [(!= i :petr)]]}))))

  (t/testing "multiple literals in set"
    (t/is (= #{[:petr] [:ivan]} (q/q (q/db *kv*) '{:find [p]
                                                   :where [[p :name n]
                                                           [(== n #{"Petr" "Ivan"})]]})))

    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [p]
                                           :where [[p :name n]
                                                   [(!= n #{"Petr"})]]})))

    (t/is (= #{} (q/q (q/db *kv*) '{:find [p]
                                    :where [[p :name n]
                                            [(== n #{})]]})))

    (t/is (= #{[:petr] [:ivan]} (q/q (q/db *kv*) '{:find [p]
                                                   :where [[p :name n]
                                                           [(!= n #{})]]})))))

(t/deftest test-simple-numeric-range-search
  (t/is (= '[[:bgp {:e i, :a :age, :v age}]
             [:range [[:sym-val {:op <, :sym age, :val 20}]]]]
           (s/conform :crux.query/where '[[i :age age]
                                          [(< age 20)]])))

  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov" :age 21}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov" :age 18}])

  (t/testing "Min search case"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(> age 20)]]})))
    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[i :age age]
                                            [(> age 21)]]})))

    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(>= age 21)]]}))))

  (t/testing "Max search case"
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(< age 20)]]})))
    (t/is (= #{} (q/q (q/db *kv*) '{:find [i]
                                    :where [[i :age age]
                                            [(< age 18)]]})))
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(<= age 18)]]})))
    (t/is (= #{[18]} (q/q (q/db *kv*) '{:find [age]
                                        :where [[:petr :age age]
                                                [(<= age 18)]]}))))

  (t/testing "Reverse symbol and value"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(<= 20 age)]]})))

    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(>= 20 age)]]})))))

(t/deftest test-mutiple-values
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" }
                            {:crux.db/id :oleg :name "Oleg"}
                            {:crux.db/id :petr :name "Petr" :follows #{:ivan :oleg}}])

  (t/testing "One way"
    (t/is (= #{[:ivan] [:oleg]} (q/q (q/db *kv*) '{:find [x]
                                                   :where [[i :name "Petr"]
                                                           [i :follows x]]}))))

  (t/testing "The other way"
    (t/is (= #{[:petr]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[x :name "Ivan"]
                                                   [i :follows x]]})))))

(t/deftest test-sanitise-join
  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov"}])
  (t/testing "Can query by single field"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [e2]
                                           :where [[e :last-name "Ivanov"]
                                                   [e :last-name name1]
                                                   [e2 :last-name name1]]})))))

(t/deftest test-basic-rules
  (t/is (= '[[:bgp {:e i, :a :age, :v age}]
             [:rule {:name over-twenty-one?, :args [age]}]]
           (s/conform :crux.query/where '[[i :age age]
                                          (over-twenty-one? age)])))

  (t/is (= [{:head '{:name over-twenty-one?, :args [age]},
             :body '[[:range [[:sym-val {:op >=, :sym age, :val 21}]]]]}
            '{:head {:name over-twenty-one?, :args [age]},
              :body [[:not [[:range [[:sym-val {:op <, :sym age, :val 21}]]]]]]}]
           (s/conform :crux.query/rules '[[(over-twenty-one? age)
                                           [(>= age 21)]]
                                          [(over-twenty-one? age)
                                           (not [(< age 21)])]])))

  (f/transact-people! *kv* [{:crux.db/id :ivan :name "Ivan" :last-name "Ivanov" :age 21}
                            {:crux.db/id :petr :name "Petr" :last-name "Petrov" :age 18}])

  (t/testing "without rule"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   [(>= age 21)]]}))))

  (t/testing "rule using same variable name as body"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   (over-twenty-one? age)]
                                           :rules [[(over-twenty-one? age)
                                                    [(>= age 21)]]]}))))

  (t/testing "rule using required bound args"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   (over-twenty-one? age)]
                                           :rules [[(over-twenty-one? [age])
                                                    [(>= age 21)]]]}))))

  (t/testing "rule using different variable name from body"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   (over-twenty-one? age)]
                                           :rules [[(over-twenty-one? x)
                                                    [(>= x 21)]]]}))))

  (t/testing "nested rules"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   (over-twenty-one? age)]
                                           :rules [[(over-twenty-one? x)
                                                    (over-twenty-one-internal? x)]
                                                   [(over-twenty-one-internal? y)
                                                    [(>= y 21)]]]}))))

  (t/testing "rule using multiple arguments"
    (t/is (= #{[:ivan]} (q/q (q/db *kv*) '{:find [i]
                                           :where [[i :age age]
                                                   (over-age? age 21)]
                                           :rules [[(over-age? [age] required-age)
                                                    [(>= age required-age)]]]}))))

  (try
    (q/q (q/db *kv*) '{:find [i]
                       :where [[i :age age]
                               (over-twenty-one? age)]
                       :rules [[(over-twenty-one? x)
                                [(>= x 21)]]
                               [(over-twenty-one? x)
                                (not [(< x 21)])]]})
    (t/is (= true false) "Expected exception")
    (catch UnsupportedOperationException e
      (t/is (re-find #"Cannot do or between rules yet: " (.getMessage e)))))

  (try
    (q/q (q/db *kv*) '{:find [i]
                       :where [[i :age age]
                               (over-twenty-one? age)]})
    (t/is (= true false) "Expected exception")
    (catch IllegalArgumentException e
      (t/is (re-find #"Unknown rule: " (.getMessage e)))))

  (try
    (q/q (q/db *kv*) '{:find [i]
                       :where [[i :age age]
                               (over-twenty-one? age)]
                       :rules [[(over-twenty-one? x)
                                (over-twenty-one? x)]]})
    (t/is (= true false) "Expected exception")
    (catch UnsupportedOperationException e
      (t/is (re-find #"Cannot do recursive rules yet: " (.getMessage e))))))
