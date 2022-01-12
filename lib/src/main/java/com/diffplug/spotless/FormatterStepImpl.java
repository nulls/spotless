/*
 * Copyright 2016-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.spotless;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Standard implementation of FormatExtension which cleanly enforces
 * separation of serializable configuration and a pure format function.
 *
 * Not an inner-class of FormatterStep so that it can stay entirely private
 * from the API.
 */
@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
abstract class FormatterStepImpl<State extends Serializable> extends LazyForwardingEquality<State> implements FormatterStep {
	private static final long serialVersionUID = 1L;

	/** Transient because only the state matters. */
	final transient String name;

	/** Transient because only the state matters. */
	final transient ThrowingEx.Supplier<State> stateSupplier;

	FormatterStepImpl(String name, ThrowingEx.Supplier<State> stateSupplier) {
		this.name = Objects.requireNonNull(name);
		this.stateSupplier = Objects.requireNonNull(stateSupplier);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	protected State calculateState() throws Exception {
		return stateSupplier.get();
	}

	static final class Standard<State extends Serializable> extends FormatterStepImpl<State> {
		private static final long serialVersionUID = 1L;

		final transient ThrowingEx.Function<State, FormatterFunc> stateToFormatter;
		transient FormatterFunc formatter; // initialized lazily

		Standard(String name, ThrowingEx.Supplier<State> stateSupplier, ThrowingEx.Function<State, FormatterFunc> stateToFormatter) {
			super(name, stateSupplier);
			this.stateToFormatter = Objects.requireNonNull(stateToFormatter);
		}

		private FormatterFunc func() throws Exception {
			if (formatter != null) {
				return formatter;
			}
			formatter = stateToFormatter.apply(state());
			return formatter;
		}

		@Override
		public String format(String rawUnix, File file) throws Exception {
			Objects.requireNonNull(rawUnix, "rawUnix");
			Objects.requireNonNull(file, "file");
			return func().apply(rawUnix, file);
		}

		@Override
		public List<Lint> lint(String content, File file) throws Exception {
			Objects.requireNonNull(content, "content");
			Objects.requireNonNull(file, "file");
			return func().lint(content, file);
		}

		void cleanupFormatterFunc() {
			if (formatter instanceof FormatterFunc.Closeable) {
				((FormatterFunc.Closeable) formatter).close();
				formatter = null;
			}
		}
	}

	/** Formatter which is equal to itself, but not to any other Formatter. */
	static class NeverUpToDate extends FormatterStepImpl<Integer> {
		private static final long serialVersionUID = 1L;

		private static final Random RANDOM = new Random();

		final transient ThrowingEx.Supplier<FormatterFunc> formatterSupplier;
		transient FormatterFunc formatter; // initialized lazily

		NeverUpToDate(String name, ThrowingEx.Supplier<FormatterFunc> formatterSupplier) {
			super(name, RANDOM::nextInt);
			this.formatterSupplier = Objects.requireNonNull(formatterSupplier, "formatterSupplier");
		}

		private FormatterFunc func() throws Exception {
			if (formatter != null) {
				return formatter;
			}
			formatter = formatterSupplier.get();
			return formatter;
		}

		@Override
		public String format(String rawUnix, File file) throws Exception {
			return func().apply(rawUnix, file);
		}

		@Override
		public List<Lint> lint(String content, File file) throws Exception {
			return func().lint(content, file);
		}
	}

	/** A dummy SENTINEL file. */
	static final File SENTINEL = new File("");

	static void checkNotSentinel(File file) {
		if (file == SENTINEL) {
			throw new IllegalArgumentException("This step requires the underlying file. If this is a test, use StepHarnessWithFile");
		}
	}
}
