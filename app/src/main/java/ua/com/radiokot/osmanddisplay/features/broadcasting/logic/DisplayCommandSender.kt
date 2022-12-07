package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import io.reactivex.Completable
import io.reactivex.Observable
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand

interface DisplayCommandSender {
    val isBusy: Observable<Boolean>

    fun send(command: DisplayCommand): Completable
}