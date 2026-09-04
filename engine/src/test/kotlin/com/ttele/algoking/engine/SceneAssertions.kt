package com.ttele.algoking.engine

import com.ttele.algoking.engine.scene.BucketScene
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * A scene is a sealed union now that a hash map is not a sequence. Tests that were
 * written before that know exactly which shape they expect, so they say so here
 * rather than sprinkling casts through every assertion.
 */
val Scene.sequence: SequenceScene get() = this as SequenceScene

val Scene.table: BucketScene get() = this as BucketScene
