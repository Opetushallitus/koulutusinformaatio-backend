(ns konfo-backend.search.query
  (:require
    [konfo-backend.koodisto.koodisto :refer [list-koodi-urit]]
    [konfo-backend.tools :refer [not-blank?]]
    [konfo-backend.search.tools :refer :all]
    [clojure.string :refer [lower-case]]
    [konfo-backend.elastic-tools :refer [->size ->from]]
    [konfo-backend.tools :refer [current-time-as-kouta-format ->koodi-with-version-wildcard ->lower-case-vec]]))

(defn- ->terms-query
  [key coll]
  (if (= 1 (count coll))
    {:term  {(keyword key) (lower-case (first coll))}}
    {:terms {(keyword key) (->lower-case-vec coll)}}))

(defn- some-hakuaika-kaynnissa
  []
  {:nested {:path "hits.hakuajat" :query {:bool {:filter [{:range {:hits.hakuajat.alkaa {:lte (current-time-as-kouta-format)}}}
                                                          {:bool  {:should [{:bool {:must_not {:exists {:field "hits.hakuajat.paattyy"}}}},
                                                                            {:range {:hits.hakuajat.paattyy {:gt (current-time-as-kouta-format)}}}]}}]}}}})

(defn- filters
  [constraints]
  (cond-> []
          (koulutustyyppi? constraints)        (conj (->terms-query :hits.koulutustyypit.keyword           (:koulutustyyppi constraints)))
          (opetuskieli? constraints)           (conj (->terms-query :hits.opetuskielet.keyword             (:opetuskieli constraints)))
          (sijainti? constraints)              (conj (->terms-query :hits.sijainti.keyword                 (:sijainti constraints)))
          (koulutusala? constraints)           (conj (->terms-query :hits.koulutusalat.keyword             (:koulutusala constraints)))
          (opetustapa? constraints)            (conj (->terms-query :hits.opetustavat.keyword              (:opetustapa constraints)))
          (valintatapa? constraints)           (conj (->terms-query :hits.valintatavat.keyword             (:valintatapa constraints)))
          (hakutapa? constraints)              (conj (->terms-query :hits.hakutavat.keyword                (:hakutapa constraints)))
          (pohjakoulutusvaatimus? constraints) (conj (->terms-query :hits.pohjakoulutusvaatimukset.keyword (:pohjakoulutusvaatimus constraints)))
          (haku-kaynnissa? constraints)        (conj (some-hakuaika-kaynnissa))))

(defn- bool
  [keyword lng constraints]
  (cond-> {}
          (not-blank? keyword)       (assoc :must {:match {(->lng-keyword "hits.terms.%s" lng) {:query (lower-case keyword) :operator "and" :fuzziness "AUTO:8,12"}}})
          (constraints? constraints) (assoc :filter (filters constraints))))

(defn query
  [keyword lng constraints]
  {:nested {:path "hits", :query {:bool (bool keyword lng constraints)}}})

(defn match-all-query
  []
  {:match_all {}})

(defn- ->name-sort
  [order lng]
  {(->lng-keyword "nimi.%s.keyword" lng) {:order order :unmapped_type "string"}})

(defn sorts
  [sort order lng]
  (case sort
    "score" [{:_score {:order order}} (->name-sort "asc" lng)]
    "name" [(->name-sort order lng)]
    [{:_score {:order order}} (->name-sort "asc" lng)]))

(defn- inner-hits-filters
  [tuleva? constraints]
  {:bool {:must {:term {"hits.onkoTuleva" tuleva?}}
          :filter (filters constraints)}})

(defn inner-hits-query
  [oid lng page size order tuleva? constraints]
  (let [size (->size size)
        from (->from page size)]
    {:bool {:must [{:term {:oid oid}}
                   {:nested {:inner_hits {:_source ["hits.koulutusOid", "hits.toteutusOid", "hits.toteutusNimi", "hits.opetuskielet", "hits.oppilaitosOid", "hits.kuva", "hits.nimi", "hits.metadata" "hits.hakuajat"]
                                          :from from
                                          :size size
                                          :sort {(str "hits.nimi." lng ".keyword") {:order order :unmapped_type "string"}}}
                             :path "hits"
                             :query (inner-hits-filters tuleva? constraints)}}]}}))

(defn inner-hits-query-osat
  [oid lng page size order tuleva?]
  (let [size (->size size)
        from (->from page size)]
    {:nested {:inner_hits {:_source ["hits.koulutusOid", "hits.toteutusOid", "hits.oppilaitosOid", "hits.kuva", "hits.nimi", "hits.metadata"]
                           :from    from
                           :size    size
                           :sort    {(str "hits.nimi." lng ".keyword") {:order order :unmapped_type "string"}}}
              :path       "hits"
              :query      {:bool {:must [{:term {"hits.onkoTuleva" tuleva?}}
                                         {:term {"hits.tarjoajat" oid}}]}}}}))

(defn external-query
  [keyword lng constraints]
  {:nested {:path "hits",
            :inner_hits {},
            :query {:bool {:must   {:match {(->lng-keyword "hits.terms.%s" lng) {:query (lower-case keyword) :operator "and" :fuzziness "AUTO:8,12"}}}
                           :filter (cond-> [{:term {"hits.onkoTuleva" false}}]
                                           (koulutustyyppi? constraints)  (conj (->terms-query :hits.koulutustyypit.keyword (:koulutustyyppi constraints))))}}}})

(defn- ->term-filter
  [field term]
  {(keyword term) {:term {field term}}})

(defn- ->term-filters
  [field terms]
  (reduce merge {} (map #(->term-filter field %) terms)))

(defn- ->filters-aggregation
  [field terms]
  {:filters {:filters (->term-filters field terms)} :aggs {:real_hits {:reverse_nested {}}}})

(defn- ->filters-aggregation-for-subentity
  [field terms]
  {:filters {:filters (->term-filters field terms)}})

(defn- koodisto-filters
  [field koodisto]
  (->filters-aggregation field (list-koodi-urit koodisto)))

(defn- koodisto-filters-for-subentity
  [field koodisto]
  (->filters-aggregation-for-subentity field (list-koodi-urit koodisto)))

(defn- koulutustyyppi-filters
  [field]
  (->filters-aggregation field '["amm" "amm-tutkinnon-osa" "amm-osaamisala" "amk" "amk-ylempi" "kandi" "kandi-ja-maisteri" "maisteri" "tohtori"]))

(defn- koulutustyyppi-filters-for-subentity
  [field]
  (->filters-aggregation-for-subentity field '["amm" "amm-tutkinnon-osa" "amm-osaamisala"]))

(defn- hakukaynnissa-filter
  []
  {:filters {:filters {:hakukaynnissa (some-hakuaika-kaynnissa)}} :aggs {:real_hits {:reverse_nested {}}}})

(defn- generate-default-aggs
  []
  {:maakunta              (koodisto-filters :hits.sijainti.keyword                 "maakunta")
   :kunta                 (koodisto-filters :hits.sijainti.keyword                 "kunta")
   :opetuskieli           (koodisto-filters :hits.opetuskielet.keyword             "oppilaitoksenopetuskieli")
   :koulutusala           (koodisto-filters :hits.koulutusalat.keyword             "kansallinenkoulutusluokitus2016koulutusalataso1")
   :koulutusalataso2      (koodisto-filters :hits.koulutusalat.keyword             "kansallinenkoulutusluokitus2016koulutusalataso2")
   :koulutustyyppi        (koulutustyyppi-filters :hits.koulutustyypit.keyword)
   :koulutustyyppitaso2   (koodisto-filters :hits.koulutustyypit.keyword           "koulutustyyppi")
   :opetustapa            (koodisto-filters :hits.opetustavat.keyword              "opetuspaikkakk")
   :valintatapa           (koodisto-filters :hits.valintatavat.keyword             "valintatapajono")
   :hakukaynnissa         (hakukaynnissa-filter)
   :hakutapa              (koodisto-filters :hits.hakutavat.keyword                "hakutapa")
   :pohjakoulutusvaatimus (koodisto-filters :hits.pohjakoulutusvaatimukset.keyword "pohjakoulutusvaatimuskonfo")})

(defn- jarjestajat-aggs
  [tuleva? constraints]
  {:inner_hits_agg {:filter (inner-hits-filters tuleva? constraints)
                     :aggs {:maakunta (koodisto-filters-for-subentity :hits.sijainti.keyword "maakunta")
                            :kunta (koodisto-filters-for-subentity :hits.sijainti.keyword "kunta")
                            :opetuskieli (koodisto-filters-for-subentity :hits.opetuskielet.keyword "oppilaitoksenopetuskieli")
                            :opetustapa (koodisto-filters-for-subentity :hits.opetustavat.keyword "opetuspaikkakk")}}})

(defn aggregations
  ([]
   (aggregations generate-default-aggs))
  ([aggs-generator]
   {:hits_aggregation {:nested {:path "hits"}, :aggs (aggs-generator)}}))

(defn- tarjoajat-aggs
  [tuleva? constraints]
  {:inner_hits_agg {:filter (inner-hits-filters tuleva? constraints)
                    :aggs {:maakunta            (koodisto-filters-for-subentity :hits.sijainti.keyword "maakunta")
                           :kunta               (koodisto-filters-for-subentity :hits.sijainti.keyword "kunta")
                           :opetuskieli         (koodisto-filters-for-subentity :hits.opetuskielet.keyword "oppilaitoksenopetuskieli")
                           :koulutusala         (koodisto-filters-for-subentity :hits.koulutusalat.keyword "kansallinenkoulutusluokitus2016koulutusalataso1")
                           :koulutusalataso2    (koodisto-filters-for-subentity :hits.koulutusalat.keyword "kansallinenkoulutusluokitus2016koulutusalataso2")
                           :koulutustyyppi      (koulutustyyppi-filters-for-subentity :hits.koulutustyypit.keyword)
                           :koulutustyyppitaso2 (koodisto-filters-for-subentity :hits.koulutustyypit.keyword "koulutustyyppi")
                           :opetustapa          (koodisto-filters-for-subentity :hits.opetustavat.keyword "opetuspaikkakk")}}})

(defn jarjestajat-aggregations
  [tuleva? constraints]
  (aggregations #(jarjestajat-aggs tuleva? constraints)))

(defn tarjoajat-aggregations
  [tuleva? constraints]
  (aggregations #(tarjoajat-aggs tuleva? constraints)))
