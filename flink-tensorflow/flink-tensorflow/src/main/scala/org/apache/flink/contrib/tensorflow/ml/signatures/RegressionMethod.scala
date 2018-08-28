package org.apache.flink.contrib.tensorflow.ml.signatures

import org.apache.flink.contrib.tensorflow.graphs.GraphMethod
import org.apache.flink.contrib.tensorflow.ml.signatures.RegressionMethod._
import org.apache.flink.contrib.tensorflow.models.savedmodel.SignatureConstants._
import org.tensorflow.Tensor
import org.tensorflow.contrib.scala.Rank._
import org.tensorflow.contrib.scala._
import org.tensorflow.example.Example

/**
  * The standard regression signature.
  *
  * See https://github.com/tensorflow/serving/blob/master/tensorflow_serving/servables/tensorflow/predict_impl.cc
  */
sealed trait RegressionMethod extends GraphMethod {
  override type Input = ExampleTensor
  override type Output = PredictionTensor
  val name = REGRESS_METHOD_NAME
}

object RegressionMethod {
  type ExampleTensor = TypedTensor[`2D`, ByteString[Example]]
  type PredictionTensor = TypedTensor[`2D`, Float]

  /**
    * Predict a vector of values from a given vector of examples.
    */
  implicit val impl = new RegressionMethod {
    type Result = PredictionTensor

    def inputs(i: ExampleTensor): Map[String, Tensor] = Map(REGRESS_INPUTS -> i)

    def outputs(o: Map[String, Tensor]): PredictionTensor = o(REGRESS_OUTPUTS).taggedAs[PredictionTensor]
  }
}

