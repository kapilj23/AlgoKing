package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.scene.KeyPairScene
import org.junit.Test

/** Scratch: prints the walkthrough so a human can read it. Deleted before commit. */
class RsaScriptDumpTest {

    @Test
    fun dump() {
        val script = AlgorithmCatalog.rsa().watchScript()
        script.steps.forEach { step ->
            val s = step.scene as KeyPairScene
            println("--- ${step.index}  ${step.kind}  layer=${s.layer?.name}")
            println("    H: ${step.headline.id} ${step.headline.args}")
            println("    S: ${step.support?.id} ${step.support?.args ?: ""}")
            println(
                "    flow=${s.flow?.title} " +
                    "${s.flow?.nodes?.map { "${it.label}=${it.value ?: "-"}" }}",
            )
            println("    bridge=${s.bridge?.cells?.map { it.character + ":" + it.code }}")
            println("    maths=${s.maths?.lines?.map { it.text }}")
            println("    chain=${s.chain.map { "${it.symbol}=${it.valueLabel ?: it.value}" }}")
            println(
                "    keys=${s.keys.map { it.label + "/" + (it.printed ?: "-") }} " +
                    "trip=${s.roundTrip?.stage} cards=${s.choices.size}",
            )
        }
        println("TOTAL STEPS = ${script.size}")
    }
}
