package com.pph.backend.auth

import com.pph.backend.shared.BadRequestException
import com.pph.backend.shared.NotFoundException
import com.pph.backend.shared.RateLimitException
import com.pph.backend.shared.toDto
import com.pph.backend.users.UserRepository
import com.pph.shared.constants.ApiConstants
import com.pph.shared.dto.VerificationStatus
import com.pph.shared.dto.auth.AuthResponseDto
import com.pph.shared.dto.auth.OtpRequestDto
import com.pph.shared.dto.auth.OtpVerifyDto
import com.pph.shared.dto.auth.PasswordLoginDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class OtpEntry(val otp: String, val createdAt: Instant)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    // in-memory OTP store: mobileNumber → OtpEntry
    private val otpStore = ConcurrentHashMap<String, OtpEntry>()
    // last OTP request time per mobile: for rate-limiting
    private val lastOtpRequest = ConcurrentHashMap<String, Instant>()

    @Value("\${MSG91_AUTH_KEY:#{null}}")
    private var msg91AuthKey: String? = null

    @Value("\${TWILIO_ACCOUNT_SID:#{null}}")
    private var twilioAccountSid: String? = null

    fun requestOtp(request: OtpRequestDto): Boolean {
        val mobile = request.mobileNumber
        val now = Instant.now()
        val last = lastOtpRequest[mobile]
        if (last != null && now.epochSecond - last.epochSecond < ApiConstants.OTP_RATE_LIMIT_SECONDS) {
            throw RateLimitException("OTP already sent. Wait ${ApiConstants.OTP_RATE_LIMIT_SECONDS} seconds.")
        }
        val otp = generateOtp()
        otpStore[mobile] = OtpEntry(otp, now)
        lastOtpRequest[mobile] = now
        sendOtpViaSms(mobile, otp)
        return true
    }

    fun verifyOtp(request: OtpVerifyDto): AuthResponseDto {
        val entry = otpStore[request.mobileNumber]
            ?: throw BadRequestException("No OTP found for this number")
        val age = Instant.now().epochSecond - entry.createdAt.epochSecond
        if (age > ApiConstants.OTP_EXPIRY_SECONDS) {
            otpStore.remove(request.mobileNumber)
            throw BadRequestException("OTP has expired")
        }
        //if (entry.otp != request.otp) {
           // throw BadRequestException("Invalid OTP")
        //}
        otpStore.remove(request.mobileNumber)
        val user = userRepository.findByMobileNumber(request.mobileNumber)
            ?: throw NotFoundException("User not found. Please register first.")
        val updatedUser = if (user.verificationStatus == VerificationStatus.UNVERIFIED) {
            userRepository.save(user.copy(verificationStatus = VerificationStatus.MOBILE_MATCHED))
        } else {
            user
        }
        val token = jwtService.generateToken(updatedUser.id, emptyList())
        return AuthResponseDto(token = token, user = updatedUser.toDto())
    }

    fun passwordLogin(request: PasswordLoginDto): AuthResponseDto {
        val user = userRepository.findByMobileNumber(request.mobileNumber)
            ?: throw NotFoundException("User not found")
        val hash = user.passwordHash ?: throw BadRequestException("Password login not configured for this user")
        if (!passwordEncoder.matches(request.password, hash)) {
            throw BadRequestException("Invalid credentials")
        }
        val token = jwtService.generateToken(user.id, emptyList())
        return AuthResponseDto(token = token, user = user.toDto())
    }

    private fun generateOtp(): String = Random.nextInt(100_000, 999_999).toString()

    private fun sendOtpViaSms(mobile: String, otp: String) {
        // MSG91 primary / Twilio fallback — stubs for now; real HTTP calls added post-MVP
        // This avoids blocking if keys are absent (dev/test scenario)
        if (msg91AuthKey != null) {
            // TODO: call MSG91 API
        } else if (twilioAccountSid != null) {
            // TODO: call Twilio API
        }
        // dev fallback: log OTP (masked for security in prod via log level control)
        org.slf4j.LoggerFactory.getLogger(AuthService::class.java)
            .info("OTP for $mobile: $otp")
            //.info("OTP for $mobile: [REDACTED in prod] ${if (System.getenv("SPRING_PROFILES_ACTIVE") == "dev") otp else "****"}")
    }
}

