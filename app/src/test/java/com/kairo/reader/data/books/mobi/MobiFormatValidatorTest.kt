package com.kairo.reader.data.books.mobi

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiFormatValidatorTest {
    @Test
    fun validateAcceptsMobiPalmDatabase() {
        MobiFormatValidator.validate(validPalmDatabase(typeCreator = "BOOKMOBI"))
    }

    @Test
    fun validateAcceptsPalmDocPrcDatabase() {
        MobiFormatValidator.validate(validPalmDatabase(typeCreator = "TEXtREAd", includeMobiMagic = false))
    }

    @Test
    fun validateRejectsEncryptedAzwWithClearMessage() {
        val error =
            runCatching {
                MobiFormatValidator.validate(validPalmDatabase(typeCreator = "BOOKMOBI", encryptionType = 2))
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("DRM-protected"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateRejectsUnrelatedPalmApplications() {
        MobiFormatValidator.validate(validPalmDatabase(typeCreator = "DATAappl", includeMobiMagic = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateRejectsOverlappingRecordOffsets() {
        val data = validPalmDatabase(typeCreator = "BOOKMOBI")
        ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            .putInt(RECORD_TABLE_OFFSET + RECORD_INFO_BYTES, FIRST_RECORD_OFFSET)

        MobiFormatValidator.validate(data)
    }

    private fun validPalmDatabase(
        typeCreator: String,
        encryptionType: Int = 0,
        includeMobiMagic: Boolean = true,
    ): ByteArray {
        val data = ByteArray(FILE_SIZE)
        typeCreator.toByteArray(Charsets.US_ASCII).copyInto(data, destinationOffset = TYPE_CREATOR_OFFSET)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(RECORD_COUNT_OFFSET, RECORD_COUNT.toShort())
        buffer.putInt(RECORD_TABLE_OFFSET, FIRST_RECORD_OFFSET)
        buffer.putInt(RECORD_TABLE_OFFSET + RECORD_INFO_BYTES, SECOND_RECORD_OFFSET)
        buffer.putShort(FIRST_RECORD_OFFSET, 1)
        buffer.putShort(FIRST_RECORD_OFFSET + ENCRYPTION_OFFSET, encryptionType.toShort())
        if (includeMobiMagic) {
            "MOBI".toByteArray(Charsets.US_ASCII)
                .copyInto(data, destinationOffset = FIRST_RECORD_OFFSET + MobiLimits.MOBI_HEADER_OFFSET)
        }
        return data
    }

    private companion object {
        private const val FILE_SIZE = 256
        private const val TYPE_CREATOR_OFFSET = 60
        private const val RECORD_COUNT_OFFSET = 76
        private const val RECORD_TABLE_OFFSET = 78
        private const val RECORD_INFO_BYTES = 8
        private const val FIRST_RECORD_OFFSET = 96
        private const val SECOND_RECORD_OFFSET = 192
        private const val ENCRYPTION_OFFSET = 12
        private const val RECORD_COUNT = 2
    }
}
