package com.wtsend.backend.libs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

/**
 * The variables map was dereferenced unconditionally, so a caller passing null
 * got an NPE from inside the renderer. EmailService.sendOtp does exactly that,
 * and because nothing calls sendOtp the fault sat there unnoticed. Guarded in
 * the renderer rather than at each call site, since every caller would
 * otherwise have to remember.
 *
 * <p>
 * The engine is mocked rather than real: a bare TemplateEngine evaluates
 * expressions through OGNL, which is not on the classpath -- Boot wires a
 * SpringTemplateEngine using SpringEL. What matters here is the context the
 * renderer builds, so that is what is asserted.
 */
@ExtendWith(MockitoExtension.class)
class EmailTemplateRenderTest {

	@Mock
	private TemplateEngine templateEngine;

	@InjectMocks
	private EmailTemplateRender renderer;

	private IContext capturedContext() {
		ArgumentCaptor<IContext> context = ArgumentCaptor.forClass(IContext.class);
		verify(templateEngine).process(eq("emails/whatever"), context.capture());
		return context.getValue();
	}

	@Test
	void aNullVariableMapRendersInsteadOfThrowing() {
		assertThatCode(() -> renderer.render("emails/whatever", null)).doesNotThrowAnyException();

		assertThat(capturedContext().getVariableNames()).isEmpty();
	}

	@Test
	void anEmptyVariableMapIsAccepted() {
		renderer.render("emails/whatever", Map.of());

		assertThat(capturedContext().getVariableNames()).isEmpty();
	}

	@Test
	void variablesReachTheContext() {
		renderer.render("emails/whatever", Map.of("verifyLink", "https://example.com/verify"));

		IContext context = capturedContext();
		assertThat(context.getVariableNames()).containsExactly("verifyLink");
		assertThat(context.getVariable("verifyLink")).isEqualTo("https://example.com/verify");
	}
}
