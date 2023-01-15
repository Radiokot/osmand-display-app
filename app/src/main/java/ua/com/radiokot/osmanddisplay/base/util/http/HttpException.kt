package ua.com.radiokot.osmanddisplay.base.util.http

import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException

class HttpException(
    val code: Int,
    val httpMessage: String,
    val body: ResponseBody?
) : IOException("$code $httpMessage") {
    constructor(response: Response) : this(
        code = response.code,
        httpMessage = response.message,
        body = response.body
    )
}