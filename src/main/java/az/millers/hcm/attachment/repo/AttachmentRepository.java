package az.millers.hcm.attachment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.attachment.domain.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('core_hr.attachment_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Attachment> findByOwnerModuleAndOwnerEntityAndOwnerIdAndDeletedFalseOrderByUploadedAtDesc(
            String ownerModule, String ownerEntity, UUID ownerId);

    List<Attachment> findByUploadedByOrderByUploadedAtDesc(String username);
}
