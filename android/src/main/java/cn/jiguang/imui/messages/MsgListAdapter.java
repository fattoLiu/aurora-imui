package cn.jiguang.imui.messages;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.jiguang.imui.BuildConfig;
import cn.jiguang.imui.R;
import cn.jiguang.imui.commons.ImageLoader;
import cn.jiguang.imui.commons.ViewHolder;
import cn.jiguang.imui.commons.models.IMessage;
import cn.jiguang.imui.utils.CircleImageView;
import cn.jiguang.imui.utils.DateFormatter;

public class MsgListAdapter<MESSAGE extends IMessage> extends RecyclerView.Adapter<ViewHolder>
        implements ScrollMoreListener.OnLoadMoreListener {

    // 文本
    private final int TYPE_RECEIVE_TXT = 0;
    private final int TYPE_SEND_TXT = 1;

    // 图片
    private final int TYPE_SEND_IMAGE = 2;
    private final int TYPE_RECEIVER_IMAGE = 3;

    // 位置
    private final int TYPE_SEND_LOCATION = 4;
    private final int TYPE_RECEIVER_LOCATION = 5;

    // 语音
    private final int TYPE_SEND_VOICE = 6;
    private final int TYPE_RECEIVER_VOICE = 7;

    //群成员变动
    private final int TYPE_GROUP_CHANGE = 8;

    //自定义消息
    private final int TYPE_CUSTOM_TXT = 9;

    private Context mContext;
    private String mSenderId;
    private HoldersConfig mHolders;
    private OnLoadMoreListener mListener;
    private List<Wrapper> mItems;
    private ImageLoader mImageLoader;
    private boolean mIsSelectedMode;
    private OnMsgClickListener<MESSAGE> mMsgClickListener;
    private OnMsgLongClickListener<MESSAGE> mMsgLongClickListener;
    private OnAvatarClickListener<MESSAGE> mAvatarClickListener;
    private SelectionListener mSelectionListener;
    private int mSelectedItemCount;
    private RecyclerView.LayoutManager mLayoutManager;
    private MessageListStyle mStyle;

    public MsgListAdapter(String senderId, ImageLoader imageLoader) {
        this(senderId, new HoldersConfig(), imageLoader);
    }

    public MsgListAdapter(String senderId, HoldersConfig holders, ImageLoader imageLoader) {
        this.mSenderId = senderId;
        this.mHolders = holders;
        this.mImageLoader = imageLoader;
        this.mItems = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_RECEIVE_TXT:
                return getHolder(parent, mHolders.mReceiveTxtLayout, mHolders.mReceiveTxtHolder, false);
            case TYPE_SEND_TXT:
                return getHolder(parent, mHolders.mSendTxtLayout, mHolders.mSendTxtHolder, true);
            case TYPE_SEND_VOICE:
                return getHolder(parent, mHolders.mSendVoiceLayout, mHolders.mSendVoiceHolder, true);
            case TYPE_RECEIVER_VOICE:
                return getHolder(parent, mHolders.mReceiveVoiceLayout, mHolders.mReceiveVoiceHolder, false);
            default:
                return null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        Wrapper wrapper = mItems.get(position);
        if (wrapper.item instanceof IMessage) {
            IMessage message = (IMessage) wrapper.item;
            switch (message.getType()) {
                case SEND_TEXT:
                    return TYPE_SEND_TXT;
                case RECEIVE_TEXT:
                    return TYPE_RECEIVE_TXT;
                case SEND_VOICE:
                    return TYPE_SEND_VOICE;
                case RECEIVE_VOICE:
                    return TYPE_RECEIVER_VOICE;
                default:
                    return TYPE_CUSTOM_TXT;
            }
        }
        return TYPE_CUSTOM_TXT;
    }

    private <HOLDER extends ViewHolder> ViewHolder getHolder(ViewGroup parent, @LayoutRes int layout,
                                                             Class<HOLDER> holderClass, boolean isSender) {
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        try {
            Constructor<HOLDER> constructor = holderClass.getDeclaredConstructor(View.class, boolean.class);
            constructor.setAccessible(true);
            HOLDER holder = constructor.newInstance(v, isSender);
            if (holder instanceof DefaultMessageViewHolder) {
                ((DefaultMessageViewHolder) holder).applyStyle(mStyle);
            }
            return holder;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Wrapper wrapper = mItems.get(position);
        if (wrapper.item instanceof IMessage) {
            ((BaseMessageViewHolder) holder).mPosition = position;
            ((BaseMessageViewHolder) holder).mContext = this.mContext;
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            ((BaseMessageViewHolder) holder).mDensity = dm.density;
            ((BaseMessageViewHolder) holder).isSelected = wrapper.isSelected;
            ((BaseMessageViewHolder) holder).imageLoader = this.mImageLoader;
            ((BaseMessageViewHolder) holder).mMsgLongClickListener = this.mMsgLongClickListener;
            ((BaseMessageViewHolder) holder).mMsgClickListener = this.mMsgClickListener;
            ((BaseMessageViewHolder) holder).mAvatarClickListener = this.mAvatarClickListener;
        }
        holder.onBind(wrapper.item);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    private class Wrapper<DATA> {
        private DATA item;
        boolean isSelected;

        Wrapper(DATA item) {
            this.item = item;
        }
    }

    /**
     * Add message to bottom of list
     *
     * @param message        message to be add
     * @param scrollToBottom if true scroll list to bottom
     */
    public void addToStart(MESSAGE message, boolean scrollToBottom) {
        Wrapper<MESSAGE> element = new Wrapper<>(message);
        mItems.add(0, element);
        notifyItemRangeInserted(0, 1);
        if (mLayoutManager != null && scrollToBottom) {
            mLayoutManager.scrollToPosition(0);
        }
    }

    /**
     * Add messages chronologically, to load last page of messages from history, use this method.
     *
     * @param messages Last page of messages.
     * @param reverse  if need to reserve messages before adding.
     */
    public void addToEnd(List<MESSAGE> messages, boolean reverse) {
        if (reverse) {
            Collections.reverse(messages);
        }

        int oldSize = mItems.size();
        for (int i = 0; i < messages.size(); i++) {
            MESSAGE message = messages.get(i);
            mItems.add(new Wrapper<>(message));
        }
        notifyItemRangeInserted(oldSize, mItems.size() - oldSize);
    }

    @SuppressWarnings("unchecked")
    private int getMessagePositionById(String id) {
        for (int i = 0; i < mItems.size(); i++) {
            Wrapper wrapper = mItems.get(i);
            if (wrapper.item instanceof IMessage) {
                MESSAGE message = (MESSAGE) wrapper.item;
                if (message.getId().contentEquals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Update message by its id.
     *
     * @param message message to be updated.
     */
    public void updateMessage(MESSAGE message) {
        updateMessage(message.getId(), message);
    }

    /**
     * Updates message by old identifier.
     *
     * @param oldId
     * @param newMessage
     */
    public void updateMessage(String oldId, MESSAGE newMessage) {
        int position = getMessagePositionById(oldId);
        if (position >= 0) {
            Wrapper<MESSAGE> element = new Wrapper<>(newMessage);
            mItems.set(position, element);
            notifyItemChanged(position);
        }
    }

    /**
     * Delete message.
     *
     * @param message message to be deleted.
     */
    public void delete(MESSAGE message) {
        deleteById(message.getId());
    }

    /**
     * Delete message by identifier.
     *
     * @param id identifier of message.
     */
    public void deleteById(String id) {
        int index = getMessagePositionById(id);
        if (index >= 0) {
            mItems.remove(index);
            notifyItemRemoved(index);
        }
    }

    /**
     * Delete messages.
     *
     * @param messages messages list to be deleted.
     */
    public void delete(List<MESSAGE> messages) {
        for (MESSAGE message : messages) {
            int index = getMessagePositionById(message.getId());
            if (index >= 0) {
                mItems.remove(index);
                notifyItemRemoved(index);
            }
        }
    }

    /**
     * Delete messages by identifiers.
     *
     * @param ids ids array of identifiers of messages to be deleted.
     */
    public void deleteByIds(String[] ids) {
        for (String id : ids) {
            int index = getMessagePositionById(id);
            if (index >= 0) {
                mItems.remove(index);
                notifyItemRemoved(index);
            }
        }
    }

    /**
     * Clear messages list.
     */
    public void clear() {
        mItems.clear();
    }

    /**
     * Enable selection mode.
     *
     * @param listener SelectionListener. To get selected messages use {@link #getSelectedMessages()}.
     */
    public void enableSelectionMode(SelectionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("SelectionListener must not be null.");
        } else {
            mSelectionListener = listener;
        }
    }

    /**
     * Disable selection mode, and deselect all items.
     */
    public void disableSelectionMode() {
        mSelectionListener = null;
        deselectAllItems();
    }

    /**
     * Get selected messages.
     *
     * @return ArrayList with selected messages.
     */
    @SuppressWarnings("unchecked")
    public ArrayList<MESSAGE> getSelectedMessages() {
        ArrayList<MESSAGE> list = new ArrayList<>();
        for (Wrapper wrapper : mItems) {
            if (wrapper.item instanceof IMessage && wrapper.isSelected) {
                list.add((MESSAGE) wrapper.item);
            }
        }
        return list;
    }

    /**
     * Delete all selected messages
     */
    public void deleteSelectedMessages() {
        List<MESSAGE> selectedMessages = getSelectedMessages();
        delete(selectedMessages);
        deselectAllItems();
    }

    /**
     * Deselect all items.
     */
    public void deselectAllItems() {
        for (int i = 0; i < mItems.size(); i++) {
            Wrapper wrapper = mItems.get(i);
            if (wrapper.isSelected) {
                wrapper.isSelected = false;
                notifyItemChanged(i);
            }
        }
        mIsSelectedMode = false;
        mSelectedItemCount = 0;
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (mSelectionListener != null) {
            mSelectionListener.onSelectionChanged(mSelectedItemCount);
        }
    }

    /**
     * Set onMsgClickListener, fires onClick event only if list is not in selection mode.
     *
     * @param listener OnMsgClickListener
     */
    public void setOnMsgClickListener(OnMsgClickListener<MESSAGE> listener) {
        mMsgClickListener = listener;
    }

    private View.OnClickListener getMsgClickListener(final Wrapper<MESSAGE> wrapper) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSelectionListener != null && mIsSelectedMode) {
                    wrapper.isSelected = !wrapper.isSelected;
                    if (wrapper.isSelected) {
                        incrementSelectedItemsCount();
                    } else {
                        decrementSelectedItemsCount();
                    }

                    MESSAGE message = (wrapper.item);
                    notifyItemChanged(getMessagePositionById(message.getId()));
                } else {
                    notifyMessageClicked(wrapper.item);
                }
            }
        };
    }

    private void incrementSelectedItemsCount() {
        mSelectedItemCount++;
        notifySelectionChanged();
    }

    private void decrementSelectedItemsCount() {
        mSelectedItemCount--;
        mIsSelectedMode = mSelectedItemCount > 0;
        notifySelectionChanged();
    }

    private void notifyMessageClicked(MESSAGE message) {
        if (mMsgClickListener != null) {
            mMsgClickListener.onMessageClick(message);
        }
    }

    /**
     * Set long click listener for item, fires only if selection mode is disabled.
     *
     * @param listener onMsgLongClickListener
     */
    public void setMsgLongClickListener(OnMsgLongClickListener<MESSAGE> listener) {
        mMsgLongClickListener = listener;
    }

    private View.OnLongClickListener getMessageLongClickListener(final Wrapper<MESSAGE> wrapper) {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mSelectionListener == null) {
                    notifyMessageLongClicked(wrapper.item);
                    return true;
                } else {
                    mIsSelectedMode = true;
                    view.callOnClick();
                    return true;
                }
            }
        };
    }

    private void notifyMessageLongClicked(MESSAGE message) {
        if (mMsgLongClickListener != null) {
            mMsgLongClickListener.onMessageLongClick(message);
        }
    }

    public void setOnAvatarClickListener(OnAvatarClickListener<MESSAGE> listener) {
        mAvatarClickListener = listener;
    }

    public void setLayoutManager(RecyclerView.LayoutManager layoutManager) {
        mLayoutManager = layoutManager;
    }

    public void setStyle(Context context, MessageListStyle style) {
        mContext = context;
        mStyle = style;
    }

    public static abstract class BaseMessageViewHolder<MESSAGE extends IMessage>
            extends ViewHolder<MESSAGE> {

        protected Context mContext;
        protected float mDensity;
        protected int mPosition;
        private boolean isSelected;
        protected ImageLoader imageLoader;
        protected OnMsgLongClickListener<MESSAGE> mMsgLongClickListener;
        protected OnMsgClickListener<MESSAGE> mMsgClickListener;
        protected OnAvatarClickListener<MESSAGE> mAvatarClickListener;

        public BaseMessageViewHolder(View itemView) {
            super(itemView);
        }

        public boolean isSelected() {
            return isSelected;
        }

        public ImageLoader getImageLoader() {
            return imageLoader;
        }
    }

    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        mListener = listener;
    }

    @Override
    public void onLoadMore(int page, int total) {
        if (null != mListener) {
            mListener.onLoadMore(page, total);
        }
    }

    public interface DefaultMessageViewHolder {
        void applyStyle(MessageListStyle style);
    }

    public interface OnLoadMoreListener {
        void onLoadMore(int page, int totalCount);
    }

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    /**
     * Callback will invoked when message item is clicked
     *
     * @param <MESSAGE>
     */
    public interface OnMsgClickListener<MESSAGE extends IMessage> {
        void onMessageClick(MESSAGE message);
    }

    /**
     * Callback will invoked when message item is long clicked
     *
     * @param <MESSAGE>
     */
    public interface OnMsgLongClickListener<MESSAGE extends IMessage> {
        void onMessageLongClick(MESSAGE message);
    }

    public interface OnAvatarClickListener<MESSAGE extends IMessage> {
        void onAvatarClick(MESSAGE message);
    }

    /**
     * Holders Config
     * Config your custom layouts and view holders into adapter.
     * You need instantiate HoldersConfig, otherwise will use default MessageListStyle.
     */
    public static class HoldersConfig {

        private Class<? extends BaseMessageViewHolder<? extends IMessage>> mSendTxtHolder;
        private Class<? extends BaseMessageViewHolder<? extends IMessage>> mReceiveTxtHolder;
        private Class<? extends BaseMessageViewHolder<? extends IMessage>> mSendVoiceHolder;
        private Class<? extends BaseMessageViewHolder<? extends IMessage>> mReceiveVoiceHolder;
        private int mSendTxtLayout;
        private int mReceiveTxtLayout;
        private int mSendVoiceLayout;
        private int mReceiveVoiceLayout;

        public HoldersConfig() {
            this.mSendTxtHolder = DefaultTxtViewHolder.class;
            this.mReceiveTxtHolder = DefaultTxtViewHolder.class;
            this.mSendVoiceHolder = DefaultVoiceViewHolder.class;
            this.mReceiveVoiceHolder = DefaultVoiceViewHolder.class;
            this.mSendTxtLayout = R.layout.item_send_text;
            this.mReceiveTxtLayout = R.layout.item_receive_txt;
            this.mSendVoiceLayout = R.layout.item_send_voice;
            this.mReceiveVoiceLayout = R.layout.item_receive_voice;
        }

        public void setSenderTxtMsg(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
                                    @LayoutRes int layout) {
            this.mSendTxtHolder = holder;
            this.mSendTxtLayout = layout;
        }

        public void setReceiverTxtMsg(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
                                      @LayoutRes int layout) {
            this.mReceiveTxtHolder = holder;
            this.mReceiveTxtLayout = layout;
        }

        public void setSenderLayout(@LayoutRes int layout) {
            this.mSendTxtLayout = layout;
        }

        public void setReceiverLayout(@LayoutRes int layout) {
            this.mReceiveTxtLayout = layout;
        }

        public void setSenderVoiceMsg(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
                                      @LayoutRes int layout) {
            this.mSendVoiceHolder = holder;
            this.mSendVoiceLayout = layout;
        }

        public void setSendVoiceLayout(@LayoutRes int layout) {
            this.mSendVoiceLayout = layout;
        }

        public void setReceiverVoiceMsg(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
                                        @LayoutRes int layout) {
            this.mReceiveVoiceHolder = holder;
            this.mReceiveVoiceLayout = layout;
        }

        public void setReceiveVoiceLayout(@LayoutRes int layout) {
            this.mReceiveVoiceLayout = layout;
        }

    }

    public static class TxtViewHolder<MESSAGE extends IMessage>
            extends BaseMessageViewHolder<MESSAGE>
            implements DefaultMessageViewHolder {

        protected TextView msgTxt;
        protected TextView date;
        protected CircleImageView avatar;
        protected boolean isSender;

        public TxtViewHolder(View itemView, boolean isSender) {
            super(itemView);
            this.isSender = isSender;
            msgTxt = (TextView) itemView.findViewById(R.id.message_tv);
            date = (TextView) itemView.findViewById(R.id.date_tv);
            avatar = (CircleImageView) itemView.findViewById(R.id.avatar_iv);
        }

        @Override
        public void onBind(final MESSAGE message) {
            msgTxt.setText(message.getText());
            date.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
            boolean isAvatarExists = message.getUserInfo().getAvatar() != null
                    && !message.getUserInfo().getAvatar().isEmpty();
            if (isAvatarExists && imageLoader != null) {
                imageLoader.loadImage(avatar, message.getUserInfo().getAvatar());
            }

            msgTxt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mMsgClickListener != null) {
                        mMsgClickListener.onMessageClick(message);
                    }
                }
            });

            msgTxt.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (mMsgLongClickListener != null) {
                        mMsgLongClickListener.onMessageLongClick(message);
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.w("MsgListAdapter", "Didn't set long click listener! Drop event.");
                        }
                    }
                    return true;
                }
            });

            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mAvatarClickListener != null) {
                        mAvatarClickListener.onAvatarClick(message);
                    }
                }
            });
        }

        @Override
        public void applyStyle(MessageListStyle style) {
            msgTxt.setMaxWidth((int) (style.getWindowWidth() * style.getBubbleMaxWidth()));
            if (isSender) {
                msgTxt.setBackground(style.getSendBubbleDrawable());
                msgTxt.setTextColor(style.getSendBubbleTextColor());
                msgTxt.setTextSize(style.getSendBubbleTextSize());
                msgTxt.setPadding(style.getSendBubblePaddingLeft(),
                        style.getSendBubblePaddingTop(),
                        style.getSendBubblePaddingRight(),
                        style.getSendBubblePaddingBottom());
            } else {
                msgTxt.setBackground(style.getReceiveBubbleDrawable());
                msgTxt.setTextColor(style.getReceiveBubbleTextColor());
                msgTxt.setTextSize(style.getReceiveBubbleTextSize());
                msgTxt.setPadding(style.getReceiveBubblePaddingLeft(),
                        style.getReceiveBubblePaddingTop(),
                        style.getReceiveBubblePaddingRight(),
                        style.getReceiveBubblePaddingBottom());
            }
            date.setTextSize(style.getDateTextSize());
            date.setTextColor(style.getDateTextColor());

            android.view.ViewGroup.LayoutParams layoutParams = avatar.getLayoutParams();
            layoutParams.width = style.getAvatarWidth();
            layoutParams.height = style.getAvatarHeight();
            avatar.setLayoutParams(layoutParams);
        }

        public TextView getMsgTextView() {
            return msgTxt;
        }

        public CircleImageView getAvatar() {
            return avatar;
        }

    }

    private static class DefaultTxtViewHolder extends TxtViewHolder<IMessage> {

        public DefaultTxtViewHolder(View itemView, boolean isSender) {
            super(itemView, isSender);

        }
    }


    private static class VoiceViewHolder<MESSAGE extends IMessage> extends BaseMessageViewHolder<MESSAGE>
            implements DefaultMessageViewHolder {

        private boolean isSender;
        protected TextView msgTxt;
        protected TextView date;
        protected CircleImageView avatar;
        protected ImageView voice;
        protected TextView length;
        protected ImageView unreadStatus;
        private boolean mSetData = false;
        private int mPlayPosition = -1;
        private final MediaPlayer mp = new MediaPlayer();
        private AnimationDrawable mVoiceAnimation;
        private FileInputStream mFIS;
        private FileDescriptor mFD;
        private boolean mIsEarPhoneOn;

        public VoiceViewHolder(View itemView, boolean isSender) {
            super(itemView);
            this.isSender = isSender;
            msgTxt = (TextView) itemView.findViewById(R.id.message_tv);
            date = (TextView) itemView.findViewById(R.id.date_tv);
            avatar = (CircleImageView) itemView.findViewById(R.id.avatar_iv);
            voice = (ImageView) itemView.findViewById(R.id.voice_iv);

            if (isSender) {
                length = (TextView) itemView.findViewById(R.id.voice_length_tv);
            } else {
                unreadStatus = (ImageView) itemView.findViewById(R.id.read_status_iv);
            }

            mp.setAudioStreamType(AudioManager.STREAM_RING);
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    return false;
                }
            });
        }

        @Override
        public void onBind(final MESSAGE message) {
            date.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
            boolean isAvatarExists = message.getUserInfo().getAvatar() != null
                    && !message.getUserInfo().getAvatar().isEmpty();
            if (isAvatarExists && imageLoader != null) {
                imageLoader.loadImage(avatar, message.getUserInfo().getAvatar());
            }
            int duration = message.getDuration();
            String lengthStr = duration + mContext.getString(R.string.symbol_second);
            int width = (int) (-0.04 * duration * duration + 4.526 * duration + 75.214);
            msgTxt.setWidth((int) (width * mDensity));
            length.setText(lengthStr);

            msgTxt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mMsgClickListener != null) {
                        mMsgClickListener.onMessageClick(message);
                    }
                    // 播放中点击了正在播放的Item 则暂停播放
                    if (isSender) {
                        voice.setImageResource(R.drawable.send_voice_anim);
                    } else {
                        voice.setImageResource(R.drawable.receive_voice_anim);
                    }

                    mVoiceAnimation = (AnimationDrawable) voice.getDrawable();
                    if (mp.isPlaying() && mPosition == getAdapterPosition()) {
                        pauseVoice();
                        mVoiceAnimation.stop();
                        // 开始播放录音
                    } else {
                        // 继续播放之前暂停的录音
                        if (mSetData && mPosition == mPlayPosition) {
                            mVoiceAnimation.start();
                            mp.start();
                            // 否则重新播放该录音或者其他录音
                        } else {
                            playVoice(mPosition, message, true);
                        }
                    }
                }
            });

            msgTxt.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (mMsgLongClickListener != null) {
                        mMsgLongClickListener.onMessageLongClick(message);
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.w("MsgListAdapter", "Didn't set long click listener! Drop event.");
                        }
                    }
                    return true;
                }
            });
        }

        private void playVoice(int position, MESSAGE message, final boolean isSender) {
            mPosition = position;
            try {
                mp.reset();
                mFIS = new FileInputStream(message.getContentFile());
                mFD = mFIS.getFD();
                mp.setDataSource(mFD);
                if (mIsEarPhoneOn) {
                    mp.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                } else {
                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                }
                mp.prepare();
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mVoiceAnimation.start();
                        mp.start();
                    }
                });
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mVoiceAnimation.stop();
                        mp.reset();
                        mSetData = false;
                        if (isSender) {
                            voice.setImageResource(R.drawable.send_voice_anim);
                        } else {
                            voice.setImageResource(R.drawable.receive_voice_anim);
                        }
                    }
                });
            } catch (Exception e) {
                Toast.makeText(mContext, mContext.getString(R.string.file_not_found_toast),
                        Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } finally {
                try {
                    if (mFIS != null) {
                        mFIS.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void pauseVoice() {
            mp.pause();
            mSetData = true;
        }

        public void setAudioPlayByEarPhone(int state) {
            AudioManager audioManager = (AudioManager) mContext
                    .getSystemService(Context.AUDIO_SERVICE);
            int currVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            if (state == 0) {
                mIsEarPhoneOn = false;
                audioManager.setSpeakerphoneOn(true);
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                        AudioManager.STREAM_VOICE_CALL);
            } else {
                mIsEarPhoneOn = true;
                audioManager.setSpeakerphoneOn(false);
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, currVolume,
                        AudioManager.STREAM_VOICE_CALL);
            }
        }

        @Override
        public void applyStyle(MessageListStyle style) {
            date.setTextSize(style.getDateTextSize());
            date.setTextColor(style.getDateTextColor());
            if (isSender) {
                voice.setImageResource(R.drawable.send_mic_3);
                msgTxt.setBackground(style.getSendBubbleDrawable());
            } else {
                voice.setImageResource(R.drawable.receive_mic_3);
                msgTxt.setBackground(style.getReceiveBubbleDrawable());
            }

            android.view.ViewGroup.LayoutParams layoutParams = avatar.getLayoutParams();
            layoutParams.width = style.getAvatarWidth();
            layoutParams.height = style.getAvatarHeight();
            avatar.setLayoutParams(layoutParams);
        }
    }

    private static class DefaultVoiceViewHolder extends VoiceViewHolder<IMessage> {

        public DefaultVoiceViewHolder(View itemView, boolean isSender) {
            super(itemView, isSender);
        }
    }
}
