package az.millers.hcm.security.crypto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that encrypts strings transparently on write and decrypts
 * them on read. Applies AES-256-GCM via {@link AesGcmEncryptor}.
 *
 * <p>Hibernate instantiates converters via {@code newInstance()} — so the
 * {@link AesGcmEncryptor} can't be injected through the constructor.
 * Spring sets it on the static field via the bean-post-process below.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static AesGcmEncryptor delegate;

    @Autowired
    public void setEncryptor(AesGcmEncryptor encryptor) {
        delegate = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return delegate == null ? attribute : delegate.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return delegate == null ? dbData : delegate.decrypt(dbData);
    }
}
