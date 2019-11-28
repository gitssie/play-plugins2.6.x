package play.api.deadbolt.authz

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.{Inject, Singleton}

import org.apache.commons.codec.binary.StringUtils
import org.apache.commons.codec.digest.DigestUtils
import play.api.http.SecretConfiguration
import play.api.libs.Codecs


@Singleton
class DefaultPasswordService @Inject() (secret:SecretConfiguration) extends PasswordService {
  /**
    * Converts the specified plaintext password (usually acquired from your application's 'new user' or 'password reset'
    * workflow) into a formatted string safe for storage.  The returned string can be safely saved with the
    * corresponding user account record (e.g. as a 'password' attribute).
    * <p/>
    * It is expected that the String returned from this method will be presented to the
    * {@link #passwordsMatch(Object, String) passwordsMatch(plaintext,encrypted)} method when performing a
    * password comparison check.
    * <h3>Usage</h3>
    * The input argument type can be any 'byte backed' {@code Object} - almost always either a
    * String or character array representing passwords (character arrays are often a safer way to represent passwords
    * as they can be cleared/nulled-out after use.  Any argument type supported by
    * {@link ByteSource.Util#isCompatible(Object)} is valid.
    * <p/>
    * For example:
    * <pre>
    * String rawPassword = ...
    * String encryptedValue = passwordService.encryptPassword(rawPassword);
    * </pre>
    * or, identically:
    * <pre>
    * char[] rawPasswordChars = ...
    * String encryptedValue = passwordService.encryptPassword(rawPasswordChars);
    * </pre>
    * <p/>
    * The resulting {@code encryptedValue} should be stored with the account to be retrieved later during a
    * login attempt.  For example:
    * <pre>
    * String encryptedValue = passwordService.encryptPassword(rawPassword);
    * ...
    * userAccount.setPassword(encryptedValue);
    * userAccount.save(); //create or update to your data store
    * </pre>
    *
    * @param plaintextPassword the raw password as 'byte-backed' object (String, character array, { @link ByteSource},
    *                          etc) usually acquired from your application's 'new user' or 'password reset' workflow.
    * @return the encrypted password, formatted for storage.
    * @throws IllegalArgumentException if the argument cannot be easily converted to bytes as defined by
    *                                  { @link ByteSource.Util#isCompatible(Object)}.
    * @see ByteSource.Util#isCompatible(Object)
    */
  override def encryptPassword(plaintextPassword: AnyRef) : String = encrypt(secret.provider)(plaintextPassword.toString)

  override def decryptPassword(encrypted: String): String = decrypt(secret.provider)(encrypted)
  /**
    * Returns {@code true} if the {@code submittedPlaintext} password matches the existing {@code saved} password,
    * {@code false} otherwise.
    * <h3>Usage</h3>
    * The {@code submittedPlaintext} argument type can be any 'byte backed' {@code Object} - almost always either a
    * String or character array representing passwords (character arrays are often a safer way to represent passwords
    * as they can be cleared/nulled-out after use.  Any argument type supported by
    * {@link ByteSource.Util#isCompatible(Object)} is valid.
    * <p/>
    * For example:
    * <pre>
    * String submittedPassword = ...
    * passwordService.passwordsMatch(submittedPassword, encryptedPassword);
    * </pre>
    * or similarly:
    * <pre>
    * char[] submittedPasswordCharacters = ...
    * passwordService.passwordsMatch(submittedPasswordCharacters, encryptedPassword);
    * </pre>
    *
    * @param submittedPlaintext a raw/plaintext password submitted by an end user/Subject.
    * @param encrypted          the previously encrypted password known to be associated with an account.
    *                           This value is expected to have been previously generated from the
    *                           { @link #encryptPassword(Object) encryptPassword} method (typically
    *                           when the account is created or the account's password is reset).
    * @return { @code true} if the { @code submittedPlaintext} password matches the existing { @code saved} password,
    *         { @code false} otherwise.
    * @see ByteSource.Util#isCompatible(Object)
    */
  override def passwordsMatch(submittedPlaintext: AnyRef, encrypted: String) : Boolean = encryptPassword(submittedPlaintext).equalsIgnoreCase(encrypted)

  def encrypt(providerOpt:Option[String]):(String => String) = {
     val provider = providerOpt.getOrElse("MD5")
     if(provider.equalsIgnoreCase("AES")){
       (plaintextPassword:String) => {
         encryptAES(plaintextPassword)
       }
     }else{
       (plaintextPassword:String) => {
         Codecs.toHexString(DigestUtils.getDigest(provider).digest(StringUtils.getBytesUtf8(plaintextPassword)))
       }
     }
  }

  def decrypt(providerOpt:Option[String]):(String => String) = {
    val provider = providerOpt.getOrElse("MD5")
    if(provider.equalsIgnoreCase("AES")){
      (plaintextPassword:String) => {
        decryptAES(plaintextPassword)
      }
    }else{
      (plaintextPassword:String) => {
        plaintextPassword
      }
    }
  }

  /**
    * Encrypt a String with the AES encryption standard using the application secret
    * @param value The String to encrypt
    * @return An hexadecimal encrypted string
    */
  def encryptAES(value: String): String = encryptAES(value, secret.secret)

  /**
    * Encrypt a String with the AES encryption standard. Private key must have a length of 16 bytes
    * @param value The String to encrypt
    * @param privateKey The key used to encrypt
    * @return An hexadecimal encrypted string
    */
  def encryptAES(value: String, privateKey: String): String = {
    val raw = privateKey.getBytes("utf-8")
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
    Codecs.toHexString(cipher.doFinal(value.getBytes("utf-8")))
  }

  /**
    * Decrypt a String with the AES encryption standard using the application secret
    * @param value An hexadecimal encrypted string
    * @return The decrypted String
    */
  def decryptAES(value: String): String = decryptAES(value, secret.secret)

  /**
    * Decrypt a String with the AES encryption standard. Private key must have a length of 16 bytes
    * @param value An hexadecimal encrypted string
    * @param privateKey The key used to encrypt
    * @return The decrypted String
    */
  def decryptAES(value: String, privateKey: String): String = {
    val raw = privateKey.getBytes("utf-8")
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, skeySpec)
    new String(cipher.doFinal(Codecs.hexStringToByte(value)))
  }

}
