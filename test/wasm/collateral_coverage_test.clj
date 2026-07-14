(ns wasm.collateral-coverage-test
  "Hosts wasm/collateral_coverage.wasm (compiled from
  wasm/collateral_coverage.kotoba, see wasm/README.md) via kototama.tender
  -- proves leasing.registry/collateral-coverage-ratio-insufficient?, the
  FIRST ratio-based sufficiency check in this fleet's check-family
  taxonomy (see leasing.registry's ns docstring), runs as a real WASM
  guest, not just as JVM Clojure.

  ABI: main is 0-arity (kotoba wasm emit rejects a parameterized main --
  :main-arity); the two real i32 inputs are written into the guest's
  exported linear memory at fixed offsets before calling main() -- see
  wasm/collateral_coverage.kotoba's header comment for the offset layout.

  Test vectors mirror test/leasing/registry_test.cljc's own cases for
  `collateral-coverage-ratio-insufficient?` exactly, so a divergence
  between the JVM Clojure original and this WASM port would show up as a
  mismatched expectation here, not just a coincidentally-passing new
  suite."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kototama.contract :as contract]
            [kototama.tender :as tender]))

(defn- wasm-bytes []
  (.readAllBytes (io/input-stream (io/file "wasm/collateral_coverage.wasm"))))

(defn- run-insufficient? [collateral-value financed-amount]
  (let [instance (tender/instantiate (wasm-bytes) [] (contract/host-caps {}))
        memory (.memory instance)]
    (.writeI32 memory 0 collateral-value)
    (.writeI32 memory 4 financed-amount)
    (tender/call-main instance)))

(deftest collateral-coverage-wasm-approves-at-exact-minimum-ratio
  (testing "collateral value == financed amount (ratio exactly 1.0, the minimum) -> sufficient"
    (is (= 0 (run-insufficient? 100000 100000)))))

(deftest collateral-coverage-wasm-approves-above-minimum-ratio
  (testing "collateral value above financed amount -> sufficient"
    (is (= 0 (run-insufficient? 120000 100000)))))

(deftest collateral-coverage-wasm-rejects-just-below-minimum-ratio
  (testing "collateral value one cent below financed amount -> insufficient (boundary)"
    (is (= 1 (run-insufficient? 99999 100000)))))

(deftest collateral-coverage-wasm-rejects-clearly-below-minimum-ratio
  (testing "collateral value well below financed amount -> insufficient"
    (is (= 1 (run-insufficient? 60000 100000)))))

(deftest collateral-coverage-wasm-handles-zero-financed-amount
  (testing "non-positive financed-amount -> not insufficient (no division by zero, matches leasing.registry's own guard)"
    (is (= 0 (run-insufficient? 0 0)))))
