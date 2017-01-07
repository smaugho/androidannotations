/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * Copyright (C) 2016 the AndroidAnnotations project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.test.receiver;

import static org.junit.Assert.assertTrue;
import static org.robolectric.util.FragmentTestUtil.startFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

@RunWith(RobolectricTestRunner.class)
public class FragmentWithReceiverTest {

	FragmentWithReceiver_ fragment;

	@Before
	public void setUp() {
		fragment = new FragmentWithReceiver_();
		startFragment(fragment);
	}

	@Test
	public void defaultReceiverCalled() {
		Intent intent = new Intent(FragmentWithReceiver.RECEIVER_ACTION);

		fragment.getActivity().sendBroadcast(intent);

		assertTrue(fragment.defaultReceiverCalled);
	}

	@Test
	public void onAttachReceiverCalled() {
		Intent intent = new Intent(FragmentWithReceiver.RECEIVER_ACTION);

		fragment.getActivity().sendBroadcast(intent);

		assertTrue(fragment.onAttachReceiverCalled);
	}

	@Test
	public void onStartReceiverCalled() {
		Intent intent = new Intent(FragmentWithReceiver.RECEIVER_ACTION);

		fragment.getActivity().sendBroadcast(intent);

		assertTrue(fragment.onStartReceiverCalled);
	}

	@Test
	public void onResumeReceiverCalled() {
		Intent intent = new Intent(FragmentWithReceiver.RECEIVER_ACTION);

		LocalBroadcastManager.getInstance(RuntimeEnvironment.application).sendBroadcast(intent);

		assertTrue(fragment.onResumeReceiverCalled);
	}
}
