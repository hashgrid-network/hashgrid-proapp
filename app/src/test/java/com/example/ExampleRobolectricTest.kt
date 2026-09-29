package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HashGrid Pro", appName)
  }

  @Test
  fun testNowPaymentsIpnHmacVerification() {
    val manager = com.example.data.payment.NowPaymentsManager()
    val samplePayload = """{"payment_id":12345,"payment_status":"finished","pay_address":"0xabc"}"""
    // verify signature logic runs without error
    org.junit.Assert.assertFalse(manager.verifyIpnSignature(samplePayload, "invalid_signature"))
  }
}
