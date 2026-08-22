package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * The line manager only became settable from the employee form when that field
 * was added; before, nobody could create a loop because nobody could set it at
 * all. A loop matters more than it looks: the reporting line is walked to
 * decide who may see whom, and that walk runs on ordinary reads.
 */
class EmployeeManagerCycleTest {

    private final EmployeeRepository repository = mock(EmployeeRepository.class);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    @DisplayName("A cannot report to B when B already reports to A")
    void directLoopIsRefused() {
        // Bob reports to Alice; making Alice report to Bob closes the loop.
        when(repository.findById(bob)).thenReturn(Optional.of(employee(bob, alice)));

        assertThatThrownBy(() -> validate(alice, bob))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("circular");
    }

    @Test
    @DisplayName("a loop further up the line is refused too")
    void indirectLoopIsRefused() {
        // Carol → Bob → Alice. Pointing Alice at Carol closes a three-person ring.
        when(repository.findById(carol)).thenReturn(Optional.of(employee(carol, bob)));
        when(repository.findById(bob)).thenReturn(Optional.of(employee(bob, alice)));

        assertThatThrownBy(() -> validate(alice, carol))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("circular");
    }

    @Test
    @DisplayName("an ordinary reporting line is accepted")
    void straightLineIsFine() {
        // Carol reports to nobody, so Alice → Carol is just a line.
        when(repository.findById(carol)).thenReturn(Optional.of(employee(carol, null)));

        assertThatCode(() -> validate(alice, carol)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no manager at all is accepted — somebody has to be at the top")
    void noManagerIsFine() {
        assertThatCode(() -> validate(alice, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a loop that already exists in the data does not hang the request fixing it")
    void preExistingLoopTerminates() {
        // Bob ↔ Carol already point at each other; walking up never reaches
        // Alice, and must stop rather than spin.
        when(repository.findById(bob)).thenReturn(Optional.of(employee(bob, carol)));
        when(repository.findById(carol)).thenReturn(Optional.of(employee(carol, bob)));

        assertThatCode(() -> validate(alice, bob)).doesNotThrowAnyException();
    }

    private static Employee employee(UUID id, UUID managerId) {
        Employee e = new Employee();
        e.setId(id);
        e.setManagerId(managerId);
        return e;
    }

    /** Calls the private validator directly — it is the whole rule under test. */
    private void validate(UUID employeeId, UUID proposedManagerId) {
        EmployeeService service = new EmployeeService(
                repository, null, null, null, null, null, null, null, null, null, null, null, null);
        try {
            Method m = EmployeeService.class.getDeclaredMethod(
                    "validateNoManagerCycle", UUID.class, UUID.class);
            m.setAccessible(true);
            m.invoke(service, employeeId, proposedManagerId);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
