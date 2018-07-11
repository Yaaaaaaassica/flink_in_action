package org.spafka

import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.watermark.Watermark

/**
  * This generator generates watermarks assuming that elements come out of order to a certain degree only.
  * The latest elements for a certain timestamp t will arrive at most n milliseconds after the earliest
  * elements for timestamp t.
  */
class BoundedOutOfOrdernessGenerator extends AssignerWithPeriodicWatermarks[MyEvent] {

  val maxOutOfOrderness: Long = 3500L // 3.5 seconds

  var currentMaxTimestamp: Long = _

  override def extractTimestamp(element: MyEvent, previousElementTimestamp: Long): Long = {
    val timestamp = element.createTime
    currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp)
    timestamp;
  }

  override def getCurrentWatermark(): Watermark = {
    // return the watermark as current highest timestamp minus the out-of-orderness bound
    new Watermark(currentMaxTimestamp - maxOutOfOrderness);
  }
}

case class MyEvent(createTime: Long)


import java.text.SimpleDateFormat

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector


object WatermarkTest {

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("USAGE:\nSocketWatermarkTest <hostname> <port>")
      System.exit(1)
    }

    val hostName = args(0)
    val port = args(1).toInt

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)

    val input = env.socketTextStream(hostName, port)

    val inputMap = input.map(f => {
      val arr = f.split(" ")
      val code = arr(0)
      val time = arr(1).toLong
      (code, time)
    })

    val watermark = inputMap.keyBy(1).assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks[(String, Long)] {

      var currentMaxTimestamp = 0L
      val maxOutOfOrderness = 0L //最大允许的乱序时间是10s

      var watermark: Watermark = null

      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

      override def getCurrentWatermark: Watermark = {
        watermark = new Watermark(currentMaxTimestamp - maxOutOfOrderness)

        // println(Thread.currentThread().getName + watermark)
        watermark
      }

      override def extractTimestamp(t: (String, Long), l: Long): Long = {
        val timestamp = t._2
        currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp)
        System.err.println(Thread.currentThread().getName + "data :" + t._1 + "," + t._2 + "| ts: " + format.format(t._2) + "," + "currentMaxTimestamp" + " " + currentMaxTimestamp + "," + watermark)
        timestamp
      }
    })

    //    val watermark= inputMap.assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[(String, Long)](Time.seconds(10)){
    //      /**
    //        * Extracts the timestamp from the given element.
    //        *
    //        * @param element The element that the timestamp is extracted from.
    //        * @return The new timestamp.
    //        */
    //      override def extractTimestamp(element: (String, Long)): Long = {
    //        element._2
    //      }
    //    })

    val window = watermark
      .keyBy(_._1)
      .window(TumblingEventTimeWindows.of(Time.seconds(1)))
      .apply(new WindowFunctionTest)

    window.print()

    env.execute()
  }

  class WindowFunctionTest extends WindowFunction[(String, Long), (String, Int, String, String, String, String), String, TimeWindow] {

    override def apply(key: String, window: TimeWindow, input: Iterable[(String, Long)], out: Collector[(String, Int, String, String, String, String)]): Unit = {

      print("windowing")
      val list = input.toList.sortBy(_._2)
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
      out.collect(key, input.size, format.format(list.head._2), format.format(list.last._2), format.format(window.getStart), format.format(window.getEnd))
    }
  }
}