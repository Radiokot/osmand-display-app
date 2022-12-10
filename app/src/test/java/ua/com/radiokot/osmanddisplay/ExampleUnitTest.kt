package ua.com.radiokot.osmanddisplay

import io.reactivex.BackpressureStrategy
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Test

import org.junit.Assert.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun backpressureTest() {
        val e = Executors.newSingleThreadExecutor()
        val s = Schedulers.from(e)

        val subject = PublishSubject.create<Int>()

        Thread {
            repeat(200) {
                subject.onNext(it)
                Thread.sleep(100)
            }
        }.start()

        subject
            .toFlowable(BackpressureStrategy.DROP)
            .observeOn(s, false, 1)
            .flatMapSingle({
                {
                    println("begin_long_processing $it")
                    Thread.sleep(500)
                    it
                }.toSingle()
                    .delay(500, TimeUnit.MILLISECONDS, s)
                    .doOnSuccess {
                        println("delay_ended $it")
                    }
            }, false, 1)
            .subscribeBy(
                onNext = { println("processed $it") }
            )
        Thread.sleep(20000)
    }
}