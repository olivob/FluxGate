package com.bryan.fluxgate.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.bryan.fluxgate.exception.ProviderException;
import com.bryan.fluxgate.exception.UnsupportedModelException;
import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.provider.MockProviderClient;
import com.bryan.fluxgate.provider.OpenAiProviderClient;

class ProviderRoutingServiceTest {
    private final MockProviderClient mock = mock(MockProviderClient.class);
    private final StaticListableBeanFactory beans = new StaticListableBeanFactory();
    private final ProviderRoutingServiceImpl router = new ProviderRoutingServiceImpl(
            mock, beans.getBeanProvider(OpenAiProviderClient.class));

    @Test
    void mockModelStillRoutesWithoutOpenAi() {
        assertSame(mock, router.route(new ChatRequest("mock-model", "Hello"), null));
    }

    @Test
    void openAiAliasSelectsTheConfiguredClient() {
        var openai = mock(OpenAiProviderClient.class);
        beans.addBean("openai", openai);
        assertSame(openai, router.route(new ChatRequest("openai", "Hello"), null));
    }

    @Test
    void disabledOpenAiDoesNotSilentlyFallBackToMock() {
        ProviderException error = assertThrows(ProviderException.class,
                () -> router.route(new ChatRequest("openai", "Hello"), null));
        assertEquals(ProviderException.Kind.NOT_CONFIGURED, error.getKind());
    }

    @Test
    void unknownModelsAreRejected() {
        assertThrows(UnsupportedModelException.class,
                () -> router.route(new ChatRequest("unknown-model", "Hello"), null));
    }
}
