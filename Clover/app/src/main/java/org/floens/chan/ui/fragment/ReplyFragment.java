/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.fragment;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Reply;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.animation.ViewFlipperAnimations;
import org.floens.chan.ui.layout.CaptchaLayout;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.ImageDecoder;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.ThemeHelper;

import java.io.File;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ReplyFragment extends DialogFragment implements CaptchaLayout.CaptchaCallback {
    private static final String TAG = "ReplyFragment";

    private int page = 0;

    private Loadable loadable;
    private boolean quickMode = false;

    private final Reply draft = new Reply();
    private boolean shouldSaveDraft = true;

    private String captchaResponse;

    private int defaultTextColor;
    private int maxCommentCount;

    // Views
    private View container;
    private ViewFlipper flipper;
    private Button cancelButton;
    private ImageButton fileButton;
    private Button submitButton;
    private EditText nameView;
    private EditText emailView;
    private EditText subjectView;
    private EditText commentView;
    private EditText fileNameView;
    private CheckBox spoilerImageView;
    private LoadView imageViewContainer;
    private CaptchaLayout captchaLayout;
    private LoadView responseContainer;
    private Button insertSpoiler;
    private Button insertCode;
    private TextView commentCountView;
    private TextView fileStatusView;

    private ActionBarActivity context;

    public static ReplyFragment newInstance(Loadable loadable, boolean quickMode) {
        ReplyFragment reply = new ReplyFragment();
        reply.loadable = loadable;
        reply.quickMode = quickMode;
        return reply;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        loadable.writeToBundle(context, outState);
        outState.putBoolean(context.getPackageName() + ".quickmode", quickMode);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        context = (ActionBarActivity) getActivity();

        if (loadable == null && savedInstanceState != null) {
            loadable = new Loadable();
            loadable.readFromBundle(context, savedInstanceState);
            quickMode = savedInstanceState.getBoolean(context.getPackageName() + ".quickmode");
        }

        if (loadable != null) {
            setClosable(true);

            Dialog dialog = getDialog();
            String title = (loadable.isThreadMode() ? context.getString(R.string.reply) : context.getString(R.string.reply_to_board)) + " " + loadable.title;

            if (dialog == null) {
                context.getSupportActionBar().setTitle(title);
            } else {
                dialog.setTitle(title);
                // todo move elsewhere
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            onBackPressed();
                            return true;
                        } else
                            return false;
                    }
                });
            }

            Reply draft = ChanApplication.getReplyManager().getReplyDraft();

            if (TextUtils.isEmpty(draft.name)) {
                draft.name = ChanSettings.getDefaultName();
            }

            nameView.setText(draft.name);
            emailView.setText(draft.email);
            subjectView.setText(draft.subject);
            commentView.setText(draft.comment);
            commentView.setSelection(draft.cursorPosition);

            setFile(draft.fileName, draft.file);
            spoilerImageView.setChecked(draft.spoilerImage);

            if (loadable.isThreadMode()) {
                subjectView.setVisibility(View.GONE);
            }

            if (quickMode) {
                nameView.setVisibility(View.GONE);
                emailView.setVisibility(View.GONE);
                subjectView.setVisibility(View.GONE);
            }

            defaultTextColor = commentView.getCurrentTextColor();

            Board b = ChanApplication.getBoardManager().getBoardByValue(loadable.board);
            if (b != null) {
                insertSpoiler.setVisibility(b.spoilers ? View.VISIBLE : View.GONE);
                insertCode.setVisibility(b.codeTags ? View.VISIBLE : View.GONE);
                maxCommentCount = b.maxCommentChars;
            }

            commentView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    showCommentCount();
                }
            });
            showCommentCount();

            String baseUrl = loadable.isThreadMode() ? ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no) : ChanUrls.getBoardUrlDesktop(loadable.board);
            captchaLayout.initCaptcha(baseUrl, ChanUrls.getCaptchaSiteKey(),
                    ThemeHelper.getInstance().getTheme().isLightTheme, ChanApplication.getReplyManager().getUserAgent(), this);
        } else {
            Logger.e(TAG, "Loadable in ReplyFragment was null");
            closeReply();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        ReplyManager replyManager = ChanApplication.getReplyManager();

        if (shouldSaveDraft) {
            draft.name = nameView.getText().toString();
            draft.email = emailView.getText().toString();
            draft.subject = subjectView.getText().toString();
            draft.comment = commentView.getText().toString();
            draft.fileName = fileNameView.getText().toString();
            draft.spoilerImage = spoilerImageView.isChecked();
            draft.cursorPosition = commentView.getSelectionStart();

            replyManager.setReplyDraft(draft);
        } else {
            replyManager.removeReplyDraft();
            setFile(null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ReplyManager replyManager = ChanApplication.getReplyManager();
        replyManager.removeFileListener();

        context = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Setup the views with listeners
        container = inflater.inflate(R.layout.reply_view, null);
        flipper = (ViewFlipper) container.findViewById(R.id.reply_flipper);

        nameView = (EditText) container.findViewById(R.id.reply_name);
        emailView = (EditText) container.findViewById(R.id.reply_email);
        subjectView = (EditText) container.findViewById(R.id.reply_subject);
        commentView = (EditText) container.findViewById(R.id.reply_comment);
        commentView.requestFocus();
        fileNameView = (EditText) container.findViewById(R.id.reply_file_name);
        spoilerImageView = (CheckBox) container.findViewById(R.id.reply_spoiler_image);

        imageViewContainer = (LoadView) container.findViewById(R.id.reply_image);
        responseContainer = (LoadView) container.findViewById(R.id.reply_response);
        captchaLayout = (CaptchaLayout) container.findViewById(R.id.captcha_layout);

        cancelButton = (Button) container.findViewById(R.id.reply_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 1) {
                    flipPage(0);
                } else {
                    closeReply();
                }
            }
        });

        fileButton = (ImageButton) container.findViewById(R.id.reply_file);
        fileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (draft.file == null) {
                    ChanApplication.getReplyManager().pickFile(new ReplyManager.FileListener() {
                        @Override
                        public void onFile(String name, File file) {
                            setFile(name, file);
                        }

                        @Override
                        public void onFileLoading() {
                            imageViewContainer.setVisibility(View.VISIBLE);
                            imageViewContainer.setView(null);
                        }
                    });
                } else {
                    setFile(null, null);
                }
            }
        });

        submitButton = (Button) container.findViewById(R.id.reply_submit);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 1 || ChanSettings.passLoggedIn()) {
                    flipPage(2);
                    submit();
                } else {
                    flipPage(1);
                }
            }
        });

        insertSpoiler = (Button) container.findViewById(R.id.insert_spoiler);
        insertSpoiler.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertAtCursor("[spoiler]", "[/spoiler]");
            }
        });

        insertCode = (Button) container.findViewById(R.id.insert_code);
        insertCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertAtCursor("[code]", "[/code]");
            }
        });

        commentCountView = (TextView) container.findViewById(R.id.reply_comment_counter);

        fileStatusView = (TextView) container.findViewById(R.id.reply_file_status);

        return container;
    }

    public boolean onBackPressed() {
        if (page == 1) {
            flipPage(0);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void captchaLoaded(CaptchaLayout captchaLayout) {
    }

    @Override
    public void captchaEntered(CaptchaLayout captchaLayout, String response) {
        captchaResponse = response;
        if (page == 1) {
            flipPage(2);
            submit();
        }
    }

    private void insertAtCursor(String before, String after) {
        int pos = commentView.getSelectionStart();
        String text = commentView.getText().toString();
        text = new StringBuilder(text).insert(pos, before + after).toString();
        commentView.setText(text);
        commentView.setSelection(pos + before.length());
    }

    private void showCommentCount() {
        int count = commentView.getText().length();
        commentCountView.setText(count + "/" + maxCommentCount);
        if (count > maxCommentCount) {
            commentCountView.setTextColor(0xffff0000);
        } else {
            commentCountView.setTextColor(defaultTextColor);
        }
    }

    private void closeReply() {
        if (getDialog() != null) {
            dismissAllowingStateLoss();
        } else {
            context.finish();
        }
    }

    /**
     * Set if the dialog is able to be closed, by pressing outside of the
     * dialog, or something else.
     */
    private void setClosable(boolean e) {
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(e);
            setCancelable(e);
        }
    }

    /**
     * Flip to an page with an animation. Sets the correct text on the
     * cancelButton:
     *
     * @param position 0-2
     */
    private void flipPage(int position) {
        boolean flipBack = position < page;

        page = position;

        if (flipBack) {
            flipper.setInAnimation(ViewFlipperAnimations.BACK_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.BACK_OUT);
        } else {
            flipper.setInAnimation(ViewFlipperAnimations.NEXT_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.NEXT_OUT);
        }
        flipper.setDisplayedChild(position);

        if (page == 0) {
            cancelButton.setText(R.string.cancel);
        } else if (page == 1) {
            cancelButton.setText(R.string.back);
        } else if (page == 2) {
            cancelButton.setText(R.string.close);
        }

        if (page == 1) {
            captchaLayout.load();
            submitButton.setEnabled(captchaResponse != null);
        } else if (page == 0) {
            submitButton.setEnabled(true);
        }
    }

    /**
     * Set the picked image in the imageView. Sets the file in the draft. Call
     * null on the file to empty the imageView.
     *
     * @param name the filename
     * @param file the file
     */
    private void setFile(final String name, final File file) {
        draft.file = file;
        draft.fileName = name;

        if (file == null) {
            fileButton.setImageResource(ThemeHelper.getInstance().getTheme().isLightTheme ? R.drawable.ic_action_attachment : R.drawable.ic_action_attachment_dark);
            imageViewContainer.removeAllViews();
            imageViewContainer.setVisibility(View.GONE);
            fileNameView.setText("");
            fileNameView.setVisibility(View.GONE);
            spoilerImageView.setVisibility(View.GONE);
            spoilerImageView.setChecked(false);
            fileStatusView.setVisibility(View.GONE);
        } else {
            fileButton.setImageResource(ThemeHelper.getInstance().getTheme().isLightTheme ? R.drawable.ic_action_cancel : R.drawable.ic_action_cancel_dark);
            fileNameView.setVisibility(View.VISIBLE);
            fileNameView.setText(name);

            Board b = ChanApplication.getBoardManager().getBoardByValue(loadable.board);
            spoilerImageView.setVisibility(b != null && b.spoilers ? View.VISIBLE : View.GONE);

            if (b != null) {
                boolean probablyWebm = name.endsWith(".webm");
                int maxSize = probablyWebm ? b.maxWebmSize : b.maxFileSize;
                if (file.length() > maxSize) {
                    String fileSize = AndroidUtils.getReadableFileSize((int) file.length(), false);
                    String maxSizeString = AndroidUtils.getReadableFileSize(maxSize, false);
                    String text = getString(probablyWebm ? R.string.reply_webm_too_big : R.string.reply_file_too_big, fileSize, maxSizeString);
                    fileStatusView.setVisibility(View.VISIBLE);
                    fileStatusView.setText(text);
                } else {
                    fileStatusView.setVisibility(View.GONE);
                }
            }

            imageViewContainer.setVisibility(View.VISIBLE);
            imageViewContainer.setView(null);
            imageViewContainer.post(new Runnable() {
                public void run() {
                    if (file.length() < 10 * 1024 * 1024) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (context == null)
                                    return;

                                final Bitmap bitmap = ImageDecoder.decodeFile(file, imageViewContainer.getWidth(), imageViewContainer.getWidth());

                                context.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (context != null) {
                                            if (bitmap != null) {
                                                ImageView imageView = new ImageView(context);
                                                imageViewContainer.setView(imageView);
                                                imageView.setAdjustViewBounds(true);
                                                imageView.setMaxWidth(imageViewContainer.getWidth());
                                                imageView.setMaxHeight(imageViewContainer.getWidth());
                                                imageView.setImageBitmap(bitmap);
                                            } else {
                                                noPreview(imageViewContainer);
                                            }
                                        }
                                    }
                                });
                            }
                        }).start();
                    } else {
                        noPreview(imageViewContainer);
                    }
                }
            });
        }
    }

    private void noPreview(LoadView loadView) {
        TextView text = new TextView(context);
        text.setLayoutParams(AndroidUtils.MATCH_WRAP_PARAMS);
        text.setGravity(Gravity.CENTER);
        text.setText(R.string.reply_no_preview);
        text.setTextSize(16f);
        int padding = dp(16);
        text.setPadding(padding, padding, padding, padding);
        loadView.setView(text);
    }

    /**
     * Submit button clicked at page 1
     */
    private void submit() {
        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        setClosable(false);

        responseContainer.setView(null);

        draft.name = nameView.getText().toString();
        draft.email = emailView.getText().toString();
        draft.subject = subjectView.getText().toString();
        draft.comment = commentView.getText().toString();
        draft.captchaResponse = captchaResponse;

        draft.fileName = "image";
        String n = fileNameView.getText().toString();
        if (!TextUtils.isEmpty(n)) {
            draft.fileName = n;
        }

        draft.resto = loadable.isThreadMode() ? loadable.no : -1;
        draft.board = loadable.board;

        if (ChanSettings.getPassEnabled()) {
            draft.usePass = true;
            draft.passId = ChanSettings.getPassId();
        }

        Board b = ChanApplication.getBoardManager().getBoardByValue(loadable.board);
        draft.spoilerImage = b != null && b.spoilers && spoilerImageView.isChecked();

        ChanApplication.getReplyManager().postReply(draft, new ReplyManager.ReplyListener() {
            @Override
            public void onResponse(ReplyManager.ReplyResponse response) {
                handleSubmitResponse(response);
            }
        });
    }

    /**
     * Got response about or reply from ReplyManager
     *
     * @param response
     */
    private void handleSubmitResponse(ReplyManager.ReplyResponse response) {
        if (context == null)
            return;

        if (response.isNetworkError || response.isUserError) {
            int resId = response.isCaptchaError ? R.string.reply_error_captcha
                    : (response.isFileError ? R.string.reply_error_file : R.string.reply_error);
            Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
            submitButton.setEnabled(true);
            cancelButton.setEnabled(true);
            setClosable(true);
            captchaResponse = null;
            if (ChanSettings.passLoggedIn()) {
                flipPage(0);
            } else {
                captchaLayout.reset();
                flipPage(1);
            }
        } else if (response.isSuccessful) {
            shouldSaveDraft = false;
            Toast.makeText(context, R.string.reply_success, Toast.LENGTH_SHORT).show();

            // Pin thread on successful post
            if (ChanSettings.getPinOnPost() && loadable.isThreadMode()) {
                ChanApplication.getWatchManager().addPin(loadable);
            }

            closeReply();
        } else {
            cancelButton.setEnabled(true);
            setClosable(true);

            WebView webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setSupportZoom(true);

            webView.loadData(response.responseData, "text/html", null);

            responseContainer.setView(webView);
        }
    }
}
