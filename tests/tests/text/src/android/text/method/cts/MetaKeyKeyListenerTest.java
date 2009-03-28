/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.text.method.cts;

import android.test.AndroidTestCase;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.DateKeyListener;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargets;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargetClass;
import dalvik.annotation.ToBeFixed;

/**
 * Test {@link MetaKeyKeyListener}.
 */
@TestTargetClass(MetaKeyKeyListener.class)
public class MetaKeyKeyListenerTest extends AndroidTestCase {
    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#onKeyDown(View, Editable, int, KeyEvent)}",
        method = "onKeyDown",
        args = {android.view.View.class, android.text.Editable.class, int.class, 
                android.view.KeyEvent.class}
    )
    public void testPressKey() {
        final CharSequence str = "123456";
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        final View view = new ImageView(getContext());
        final Editable content = Editable.Factory.getInstance().newEditable(str);

        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 0, 0, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown( view,
                                     content,
                                     KeyEvent.KEYCODE_0,
                                     new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
        assertEquals('0', content.charAt(0));

        content.setSpan(Selection.SELECTION_START, 1, 1, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 1, 1, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown( view,
                                     content,
                                     KeyEvent.KEYCODE_2,
                                     new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2));
        assertEquals('2', content.charAt(1));

        content.setSpan(Selection.SELECTION_START, 3, 3, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 3, 3, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown( view,
                                     content,
                                     KeyEvent.KEYCODE_3,
                                     new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3));
        assertEquals('3', content.charAt(3));
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#onKeyUp(View, Editable, int, KeyEvent)}",
        method = "onKeyUp",
        args = {android.view.View.class, android.text.Editable.class, int.class, 
                android.view.KeyEvent.class}
    )
    public void testReleaseKey() {
        final CharSequence str = "123456";
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        final View view = new ImageView(getContext());
        final Editable content = Editable.Factory.getInstance().newEditable(str);

        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 0, 0, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp( view,
                                   content,
                                   KeyEvent.KEYCODE_0,
                                   new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
        assertEquals(str.charAt(0), content.charAt(0));

        content.setSpan(Selection.SELECTION_START, 1, 1, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 1, 1, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp( view,
                                   content,
                                   KeyEvent.KEYCODE_2,
                                   new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2));
        assertEquals(str.charAt(1), content.charAt(1));

        content.setSpan(Selection.SELECTION_START, 3, 3, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 3, 3, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp( view,
                                   content,
                                   KeyEvent.KEYCODE_3,
                                   new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3));
        assertEquals(str.charAt(3), content.charAt(3));
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#adjustMetaAfterKeypress(Spannable)}",
        method = "adjustMetaAfterKeypress",
        args = {android.text.Spannable.class}
    )
    public void testAdjustMetaAfterKeypress() {
        CharSequence str = "123456";
        Spannable content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        int len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#resetMetaState(Spannable)}",
        method = "resetMetaState",
        args = {android.text.Spannable.class}
    )
    public void testResetMetaState() {
        CharSequence str = "123456";
        Spannable text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "getMetaState",
            args = {java.lang.CharSequence.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "getMetaState",
            args = {java.lang.CharSequence.class, int.class}
        )
    })
    public void testGetMetaState() {
        assertEquals(0, MetaKeyKeyListener.getMetaState("123456"));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc"));
        assertEquals(0, MetaKeyKeyListener.getMetaState("@#$$#^$^"));

        assertEquals( 0,
                      MetaKeyKeyListener.getMetaState("123456"),
                      MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals( 0,
                      MetaKeyKeyListener.getMetaState("abc"),
                      MetaKeyKeyListener.META_ALT_ON);
        assertEquals( 0,
                      MetaKeyKeyListener.getMetaState("@#$$#^$^"),
                      MetaKeyKeyListener.META_SYM_ON);

        assertEquals(0, MetaKeyKeyListener.getMetaState("123456", 0));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc", -1));
        assertEquals(0, MetaKeyKeyListener.getMetaState("@#$$#^$^", Integer.MAX_VALUE));

        assertEquals(0,
                MetaKeyKeyListener.getMetaState("123456", MetaKeyKeyListener.META_SHIFT_ON));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc", MetaKeyKeyListener.META_ALT_ON));
        assertEquals(0,
                MetaKeyKeyListener.getMetaState("@#$$#^$^", MetaKeyKeyListener.META_SYM_ON));
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#isMetaTracker(CharSequence, Object)}",
        method = "isMetaTracker",
        args = {java.lang.CharSequence.class, java.lang.Object.class}
    )
    public void testIsMetaTracker() {
        assertFalse(MetaKeyKeyListener.isMetaTracker("123456", new Object()));
        assertFalse(MetaKeyKeyListener.isMetaTracker("abc", new Object()));
        assertFalse(MetaKeyKeyListener.isMetaTracker("@#$$#^$^", new Object()));
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link MetaKeyKeyListener#resetLockedMeta(Spannable)}",
        method = "resetLockedMeta",
        args = {android.text.Spannable.class}
    )
    @ToBeFixed(bug="1371108", explanation="NPE is not expected.")
    public void testResetLockedMeta() {
        MockMetaKeyKeyListener mockMetaKeyKeyListener = new MockMetaKeyKeyListener();

        try {
            MockSpannable str = new MockSpannable();
            str.setSpan(new Object(), 0, 0,
                    Spannable.SPAN_MARK_MARK | (4 << Spannable.SPAN_USER_SHIFT));
            assertFalse(str.hasCalledRemoveSpan());
            mockMetaKeyKeyListener.callResetLockedMeta(str);
            assertTrue(str.hasCalledRemoveSpan());

            str = new MockSpannable();
            str.setSpan(new Object(), 0, 0, Spannable.SPAN_MARK_POINT);
            assertFalse(str.hasCalledRemoveSpan());
            mockMetaKeyKeyListener.callResetLockedMeta(str);
            assertFalse(str.hasCalledRemoveSpan());
        } catch (Exception e) {
            fail("should not throw exception.");
        }

        try {
            mockMetaKeyKeyListener.callResetLockedMeta(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        method = "clearMetaKeyState",
        args = {android.view.View.class, android.text.Editable.class, int.class}
    )
    public void testClearMetaKeyState() {
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        CharSequence str = "123456";
        Editable text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_ALT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_SYM_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));
    }

    private class MockMetaKeyKeyListener extends MetaKeyKeyListener {
        public void callResetLockedMeta(Spannable content) {
            MetaKeyKeyListener.resetLockedMeta(content);
        }
    }

    private class MockSpannable implements Spannable {
        private int mFlags;
        private boolean mCalledRemoveSpan = false;

        public boolean hasCalledRemoveSpan() {
            return mCalledRemoveSpan;
        }

        public void setSpan(Object what, int start, int end, int flags) {
            mFlags = flags;
        }

        public void removeSpan(Object what) {
            mCalledRemoveSpan = true;
        }

        public <T> T[] getSpans(int start, int end, Class<T> type) {
            return null;
        }

        public int getSpanStart(Object tag) {
            return 0;
        }

        public int getSpanEnd(Object tag) {
            return 0;
        }

        public int getSpanFlags(Object tag) {
            return mFlags;
        }

        @SuppressWarnings("unchecked")
        public int nextSpanTransition(int start, int limit, Class type) {
            return 0;
        }

        public char charAt(int index) {
            return 0;
        }

        public int length() {
            return 0;
        }

        public CharSequence subSequence(int start, int end) {
            return null;
        }
    }
}
