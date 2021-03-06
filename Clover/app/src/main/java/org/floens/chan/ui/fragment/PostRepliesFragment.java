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

import android.app.Activity;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.ui.helper.PostPopupHelper;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.ThemeHelper;

/**
 * A DialogFragment that shows a list of posts. Use the newInstance method for
 * instantiating.
 */
public class PostRepliesFragment extends DialogFragment {
    private ListView listView;

    private Activity activity;
    private PostPopupHelper.RepliesData repliesData;
    private PostPopupHelper postPopupHelper;
    private ThreadPresenter presenter;

    public static PostRepliesFragment newInstance(PostPopupHelper.RepliesData repliesData, PostPopupHelper postPopupHelper, ThreadPresenter presenter) {
        PostRepliesFragment fragment = new PostRepliesFragment();

        fragment.repliesData = repliesData;
        fragment.postPopupHelper = postPopupHelper;
        fragment.presenter = presenter;
        return fragment;
    }

    public void dismissNoCallback() {
        postPopupHelper = null;
        dismiss();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        if (postPopupHelper != null) {
//            postPopupHelper.onPostRepliesPop();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup unused, Bundle savedInstanceState) {
        View container;
        if (ChanSettings.getReplyButtonsBottom()) {
            container = inflater.inflate(R.layout.post_replies_bottombuttons, null);
        } else {
            container = inflater.inflate(R.layout.post_replies, null);
        }

        listView = (ListView) container.findViewById(R.id.post_list);

        container.findViewById(R.id.replies_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        container.findViewById(R.id.replies_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (postPopupHelper != null) {
//                    postPopupHelper.closeAllPostFragments();
                }
            }
        });

        if (!ThemeHelper.getInstance().getTheme().isLightTheme) {
            ((TextView) container.findViewById(R.id.replies_back_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_back_dark, 0, 0, 0);
            ((TextView) container.findViewById(R.id.replies_close_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_done_dark, 0, 0, 0);
        }

        return container;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = getActivity();

        if (repliesData == null) {
            // Restoring from background.
            dismiss();
        } else {
            ArrayAdapter<Post> adapter = new ArrayAdapter<Post>(activity, 0) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    PostView postView;
                    if (convertView instanceof PostView) {
                        postView = (PostView) convertView;
                    } else {
                        postView = new PostView(activity);
                    }

                    final Post p = getItem(position);

                    postView.setPost(p, presenter, false);
                    postView.setHighlightQuotesWithNo(repliesData.forPost.no);
                    postView.setOnClickListeners(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (postPopupHelper != null) {
                                postPopupHelper.postClicked(p);
                            }
                            dismiss();
                        }
                    });

                    return postView;
                }
            };

            adapter.addAll(repliesData.posts);
            listView.setAdapter(adapter);

            listView.setSelectionFromTop(repliesData.listViewIndex, repliesData.listViewTop);
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (repliesData != null) {
                        repliesData.listViewIndex = view.getFirstVisiblePosition();
                        View v = view.getChildAt(0);
                        repliesData.listViewTop = (v == null) ? 0 : v.getTop();
                    }
                }
            });
        }
    }
}
