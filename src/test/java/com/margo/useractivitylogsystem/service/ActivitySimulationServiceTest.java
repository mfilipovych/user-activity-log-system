package com.margo.useractivitylogsystem.service;

import com.margo.useractivitylogsystem.model.ActivityRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitySimulationServiceTest {

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private Random random;

    @InjectMocks
    private ActivitySimulationService service;

    @Captor
    private ArgumentCaptor<UUID> userIdCaptor;

    @Captor
    private ArgumentCaptor<ActivityRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "random", random);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void simulateActivityLog_picksEachListElementAtTheRandomIndex(int index) {
        // given
        when(random.nextInt(anyInt())).thenReturn(index);

        // when
        service.simulateActivityLog();

        // then
        verify(userActivityService).saveActivity(userIdCaptor.capture(), requestCaptor.capture());
        ActivityRequest request = requestCaptor.getValue();

        assertThat(userIdCaptor.getValue()).isEqualTo(mockValues("MOCK_USER_IDS").get(index));
        assertThat(request.activityType()).isEqualTo(mockValues("MOCK_ACTIVITY_TYPES").get(index));
        assertThat(request.details()).isEqualTo(mockValues("MOCK_DETAILS").get(index));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void simulateActivityLog_doesNotPropagateSaveFailures(RuntimeException failure) {
        // given
        when(userActivityService.saveActivity(any(UUID.class), any(ActivityRequest.class))).thenThrow(failure);

        // when / then
        assertThatCode(() -> service.simulateActivityLog()).doesNotThrowAnyException();
        verify(userActivityService).saveActivity(any(UUID.class), any(ActivityRequest.class));
    }

    private static Stream<RuntimeException> failures() {
        return Stream.of(
                new RuntimeException(),
                new IllegalStateException(),
                new DataAccessResourceFailureException("..."));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> mockValues(String staticFieldName) {
        return (List<T>) ReflectionTestUtils.getField(ActivitySimulationService.class, staticFieldName);
    }
}