package me.saket.dank.ui.submission;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;
import static io.reactivex.schedulers.Schedulers.io;
import static me.saket.dank.utils.RxUtils.applySchedulersCompletable;
import static me.saket.dank.utils.RxUtils.doNothing;
import static me.saket.dank.utils.RxUtils.doNothingCompletable;
import static me.saket.dank.utils.RxUtils.logError;
import static me.saket.dank.utils.Views.executeOnMeasure;
import static me.saket.dank.utils.Views.setHeight;
import static me.saket.dank.utils.Views.touchLiesOn;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.transition.ChangeBounds;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.transition.TransitionSet;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.Toast;

import com.alexvasilkov.gestures.GestureController;
import com.alexvasilkov.gestures.State;
import com.danikula.videocache.HttpProxyCacheServer;
import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.PublishRelay;
import com.jakewharton.rxrelay2.Relay;
import com.squareup.moshi.Moshi;

import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thumbnails;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import butterknife.BindDimen;
import butterknife.BindDrawable;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import me.saket.dank.R;
import me.saket.dank.data.ErrorResolver;
import me.saket.dank.data.LinkMetadataRepository;
import me.saket.dank.data.PostedOrInFlightContribution;
import me.saket.dank.data.ResolvedError;
import me.saket.dank.data.StatusBarTint;
import me.saket.dank.data.UserPreferences;
import me.saket.dank.data.VotingManager;
import me.saket.dank.data.exceptions.ImgurApiRequestRateLimitReachedException;
import me.saket.dank.data.links.ExternalLink;
import me.saket.dank.data.links.ImgurAlbumLink;
import me.saket.dank.data.links.Link;
import me.saket.dank.data.links.MediaLink;
import me.saket.dank.data.links.UnresolvedMediaLink;
import me.saket.dank.di.Dank;
import me.saket.dank.ui.UrlRouter;
import me.saket.dank.ui.authentication.LoginActivity;
import me.saket.dank.ui.compose.ComposeReplyActivity;
import me.saket.dank.ui.compose.ComposeStartOptions;
import me.saket.dank.ui.giphy.GiphyGif;
import me.saket.dank.ui.giphy.GiphyPickerActivity;
import me.saket.dank.ui.media.MediaHostRepository;
import me.saket.dank.ui.submission.adapter.CommentsDiffCallback;
import me.saket.dank.ui.submission.adapter.ImageWithMultipleVariants;
import me.saket.dank.ui.submission.adapter.SubmissionCommentInlineReply;
import me.saket.dank.ui.submission.adapter.SubmissionCommentsAdapter;
import me.saket.dank.ui.submission.adapter.SubmissionCommentsHeader;
import me.saket.dank.ui.submission.adapter.SubmissionScreenUiModel;
import me.saket.dank.ui.submission.adapter.SubmissionScreenUiModelConstructor;
import me.saket.dank.ui.submission.events.ReplyInsertGifClickEvent;
import me.saket.dank.ui.submission.events.ReplyItemViewBindEvent;
import me.saket.dank.ui.submission.events.ReplySendClickEvent;
import me.saket.dank.ui.subreddits.SubmissionPageAnimationOptimizer;
import me.saket.dank.ui.subreddits.SubredditActivity;
import me.saket.dank.ui.user.UserSessionRepository;
import me.saket.dank.utils.Animations;
import me.saket.dank.utils.DankSubmissionRequest;
import me.saket.dank.utils.ExoPlayerManager;
import me.saket.dank.utils.Function0;
import me.saket.dank.utils.Keyboards;
import me.saket.dank.utils.NetworkStateListener;
import me.saket.dank.utils.Optional;
import me.saket.dank.utils.Pair;
import me.saket.dank.utils.RxDiffUtils;
import me.saket.dank.utils.RxUtils;
import me.saket.dank.utils.UrlParser;
import me.saket.dank.utils.Views;
import me.saket.dank.utils.itemanimators.SubmissionCommentsItemAnimator;
import me.saket.dank.utils.lifecycle.LifecycleOwnerActivity;
import me.saket.dank.utils.lifecycle.LifecycleOwnerViews;
import me.saket.dank.utils.lifecycle.LifecycleOwnerViews.ViewLifecycleStreams;
import me.saket.dank.utils.lifecycle.LifecycleStreams;
import me.saket.dank.widgets.AnimatedToolbarBackground;
import me.saket.dank.widgets.InboxUI.ExpandablePageLayout;
import me.saket.dank.widgets.KeyboardVisibilityDetector.KeyboardVisibilityChangeEvent;
import me.saket.dank.widgets.ScrollingRecyclerViewSheet;
import me.saket.dank.widgets.SubmissionAnimatedProgressBar;
import me.saket.dank.widgets.ZoomableImageView;
import me.saket.dank.widgets.swipe.RecyclerSwipeListener;
import timber.log.Timber;

@SuppressLint("SetJavaScriptEnabled")
public class SubmissionPageLayout extends ExpandablePageLayout
    implements ExpandablePageLayout.StateChangeCallbacks, ExpandablePageLayout.OnPullToCollapseIntercepter
{

  private static final String KEY_SUPER_CLASS_STATE = "superClassState";
  private static final String KEY_WAS_PAGE_EXPANDED_OR_EXPANDING = "pageState";
  private static final String KEY_SUBMISSION_JSON = "submissionJson";
  private static final String KEY_SUBMISSION_REQUEST = "submissionRequest";
  private static final String KEY_INLINE_REPLY_ROW_ID = "inlineReplyRowId";
  private static final long COMMENT_LIST_ITEM_CHANGE_ANIM_DURATION = ExpandablePageLayout.DEFAULT_ANIM_DURATION;
  private static final long ACTIVITY_CONTENT_RESIZE_ANIM_DURATION = 300;
  private static final int REQUEST_CODE_PICK_GIF = 98;
  private static final int REQUEST_CODE_FULLSCREEN_REPLY = 99;

  @BindView(R.id.submission_toolbar) View toolbar;
  @BindView(R.id.submission_toolbar_close) ImageButton toolbarCloseButton;
  @BindView(R.id.submission_toolbar_background) AnimatedToolbarBackground toolbarBackground;
  @BindView(R.id.submission_content_progress_bar) SubmissionAnimatedProgressBar contentLoadProgressView;
  @BindView(R.id.submission_image) ZoomableImageView contentImageView;
  @BindView(R.id.submission_video_container) ViewGroup contentVideoViewContainer;
  @BindView(R.id.submission_video) VideoView contentVideoView;
  @BindView(R.id.submission_comment_list_parent_sheet) ScrollingRecyclerViewSheet commentListParentSheet;
  @BindView(R.id.submission_comment_list) RecyclerView commentRecyclerView;
  @BindView(R.id.submission_reply) FloatingActionButton replyFAB;

  @BindDrawable(R.drawable.ic_toolbar_close_24dp) Drawable closeIconDrawable;
  @BindDimen(R.dimen.submission_commentssheet_minimum_visible_height) int commentsSheetMinimumVisibleHeight;

  // TODO: Convert all to lazy injections.
  @Inject SubmissionRepository submissionRepository;
  @Inject MediaHostRepository mediaHostRepository;
  @Inject UrlRouter urlRouter;
  @Inject Moshi moshi;
  @Inject LinkMetadataRepository linkMetadataRepository;
  @Inject ReplyRepository replyRepository;
  @Inject VotingManager votingManager;
  @Inject UserSessionRepository userSessionRepository;
  @Inject ErrorResolver errorResolver;
  @Inject UserPreferences userPreferences;
  @Inject NetworkStateListener networkStateListener;
  @Inject HttpProxyCacheServer httpProxyCacheServer;
  @Inject SubmissionScreenUiModelConstructor submissionScreenUiModelConstructor;
  @Inject SubmissionCommentsAdapter submissionCommentsAdapter;
  @Inject CommentTreeConstructor commentTreeConstructor;

  private BehaviorRelay<DankSubmissionRequest> submissionRequestStream = BehaviorRelay.create();
  private BehaviorRelay<Submission> submissionStream = BehaviorRelay.create();
  private BehaviorRelay<Link> submissionContentStream = BehaviorRelay.create();
  private BehaviorRelay<KeyboardVisibilityChangeEvent> keyboardVisibilityChangeStream = BehaviorRelay.create();
  private Relay<PostedOrInFlightContribution> inlineReplyStream = PublishRelay.create();
  private Relay<Optional<Link>> contentLinkStream = BehaviorRelay.create();
  private Relay<Boolean> commentsLoadProgressVisibleStream = PublishRelay.create();

  private CompositeDisposable onCollapseSubscriptions = new CompositeDisposable();
  private ExpandablePageLayout submissionPageLayout;
  private SubmissionVideoHolder contentVideoViewHolder;
  private SubmissionImageHolder contentImageViewHolder;
  private int deviceDisplayWidth, deviceDisplayHeight;
  private boolean isCommentSheetBeneathImage;
  private SubmissionPageLifecycleStreams lifecycleStreams;

  public interface Callbacks {

    SubmissionPageAnimationOptimizer submissionPageAnimationOptimizer();

    void onClickSubmissionToolbarUp();
  }

  public SubmissionPageLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    Dank.dependencyInjector().inject(this);
    LayoutInflater.from(context).inflate(R.layout.fragment_submission, this, true);
    ButterKnife.bind(this, this);

    LifecycleOwnerActivity parentLifecycleOwner = (LifecycleOwnerActivity) getContext();
    ViewLifecycleStreams viewLifecycleStreams = LifecycleOwnerViews.create(this, parentLifecycleOwner);
    lifecycleStreams = SubmissionPageLifecycleStreams.wrap(this, viewLifecycleStreams);

    // Get the display width, that will be used in populateUi() for loading an optimized image for the user.
    deviceDisplayWidth = getResources().getDisplayMetrics().widthPixels;
    deviceDisplayHeight = getResources().getDisplayMetrics().heightPixels;

    lifecycle().viewAttaches()
        .take(1)
        .takeUntil(lifecycle().viewDetaches())
        .subscribe(o -> onViewFirstAttach());

    lifecycle().onDestroy()
        .take(1)
        .subscribe(o -> onCollapseSubscriptions.clear());
  }

  public void onViewFirstAttach() {
    executeOnMeasure(toolbar, () -> setHeight(toolbarBackground, toolbar.getHeight()));
    //noinspection ConstantConditions
    toolbarCloseButton.setOnClickListener(v -> ((Callbacks) getContext()).onClickSubmissionToolbarUp());

    submissionPageLayout = this;
    submissionPageLayout.addStateChangeCallbacks(this);
    submissionPageLayout.setPullToCollapseIntercepter(this);

    Keyboards.streamKeyboardVisibilityChanges(((Activity) getContext()), Views.statusBarHeight(getResources()))
        .takeUntil(lifecycle().onDestroy())
        .subscribe(keyboardVisibilityChangeStream);

    setupCommentRecyclerView();
    setupContentImageView(this);
    setupContentVideoView();
    setupCommentsSheet();
    setupStatusBarTint();
    setupReplyFAB();
    setupSoftInputModeChangesAnimation();

    // Load comments when submission changes.
    submissionRequestStream
        .observeOn(mainThread())
        .doOnNext(o -> commentsLoadProgressVisibleStream.accept(true))
        .switchMap(submissionRequest -> submissionRepository.submissionWithComments(submissionRequest)
            .flatMap(pair -> {
              // It's possible for the remote to suggest a different sort than what was asked by SubredditActivity.
              // In that case, trigger another request with the correct sort.
              DankSubmissionRequest updatedRequest = pair.first;
              if (updatedRequest != submissionRequest) {
                //noinspection ConstantConditions
                submissionRequestStream.accept(updatedRequest);
                return Observable.never();
              } else {
                //noinspection ConstantConditions
                return Observable.just(pair.second);
              }
            })
            .takeUntil(lifecycle().onPageAboutToCollapse())
            .compose(RxUtils.applySchedulers())
            .doOnNext(o -> commentsLoadProgressVisibleStream.accept(false))
            .doOnError(error -> Timber.e(error))
            .onErrorResumeNext(Observable.never()))
        .takeUntil(lifecycle().onDestroy())
        .subscribe(submissionStream);
  }

  @Nullable
  @Override
  protected Parcelable onSaveInstanceState() {
    Bundle outState = new Bundle();
    if (submissionRequestStream.getValue() != null) {
      outState.putParcelable(KEY_SUBMISSION_REQUEST, submissionRequestStream.getValue());

      if (submissionStream.getValue() != null && submissionStream.getValue().getComments() == null) {
        // Comments haven't fetched yet == no submission cached in DB. For us to be able to immediately
        // show UI on orientation change, we unfortunately will have to manually retain this submission.
        outState.putString(KEY_SUBMISSION_JSON, moshi.adapter(Submission.class).toJson(submissionStream.getValue()));
      }
    }

    outState.putBoolean(KEY_WAS_PAGE_EXPANDED_OR_EXPANDING, isExpandedOrExpanding());
    outState.putParcelable(KEY_SUPER_CLASS_STATE, super.onSaveInstanceState());
    return outState;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    Bundle savedState = (Bundle) state;

    boolean willPageExpandAgain = savedState.getBoolean(KEY_WAS_PAGE_EXPANDED_OR_EXPANDING, false);
    if (willPageExpandAgain && savedState.containsKey(KEY_SUBMISSION_REQUEST)) {
      Submission retainedSubmission = null;
      DankSubmissionRequest retainedRequest = savedState.getParcelable(KEY_SUBMISSION_REQUEST);

      if (savedState.containsKey(KEY_SUBMISSION_JSON)) {
        String retainedSubmissionJson = savedState.getString(KEY_SUBMISSION_JSON);
        try {
          //noinspection ConstantConditions
          retainedSubmission = moshi.adapter(Submission.class).fromJson(retainedSubmissionJson);
        } catch (IOException e) {
          Timber.e(e, "Couldn't deserialize submission: %s", retainedSubmissionJson);
        }
      }

      populateUi(retainedSubmission, retainedRequest);
    }

    Parcelable superState = savedState.getParcelable(KEY_SUPER_CLASS_STATE);
    super.onRestoreInstanceState(superState);
  }

  /**
   * Data from persistence flows to this screen in a unidirectional manner. Any modifications are
   * made on {@link CommentTreeConstructor} or {@link SubmissionScreenUiModelConstructor} and
   * {@link SubmissionCommentsAdapter} subscribes to its updates.
   */
  private void setupCommentRecyclerView() {
    int itemElevation = getResources().getDimensionPixelSize(R.dimen.submission_comment_elevation);
    SimpleItemAnimator itemAnimator = new SubmissionCommentsItemAnimator(itemElevation)
        .withInterpolator(Animations.INTERPOLATOR)
        .withRemoveDuration(COMMENT_LIST_ITEM_CHANGE_ANIM_DURATION)
        .withAddDuration(COMMENT_LIST_ITEM_CHANGE_ANIM_DURATION);
    commentRecyclerView.setItemAnimator(itemAnimator);

    // RecyclerView automatically handles saving and restoring scroll position if the
    // adapter contents are the same once the adapter is set. So we set the adapter
    // only once its dataset is available.
    submissionCommentsAdapter.dataChanges()
        .take(1)
        .takeUntil(lifecycle().onDestroy())
        .subscribe(o -> commentRecyclerView.setAdapter(submissionCommentsAdapter));

    // Add pending-sync replies to the comment tree.
    submissionStream
        .observeOn(io())
        .switchMap(submissionWithComments -> replyRepository.streamPendingSyncReplies(ParentThread.of(submissionWithComments))
            .map(pendingSyncReplies -> Pair.create(submissionWithComments, pendingSyncReplies)))
        .takeUntil(lifecycle().onDestroy())
        .subscribe(pair -> {
          Submission submission = pair.first();
          List<PendingSyncReply> pendingSyncReplies = pair.second();
          commentTreeConstructor.update(submission, submission.getComments(), pendingSyncReplies);
        });

    // Adapter data-set.
    submissionScreenUiModelConstructor
        .stream(
            getContext(),
            submissionStream.observeOn(io()),
            contentLinkStream.observeOn(io()),
            commentTreeConstructor.streamTreeUpdates().observeOn(io())
        )
        .toFlowable(BackpressureStrategy.LATEST)
        .compose(RxDiffUtils.calculateDiff(CommentsDiffCallback::create))
        .subscribeOn(io())
        .observeOn(mainThread())
        .takeUntil(lifecycle().onDestroyFlowable())
        .onErrorResumeNext(error -> {
          // TODO: Handle load error.
          Timber.e(error);
          return Flowable.never();
        })
        .subscribe(
            itemsAndDiff -> {
              List<SubmissionScreenUiModel> newComments = itemsAndDiff.first();
              DiffUtil.DiffResult commentsDiffResult = itemsAndDiff.second();
              submissionCommentsAdapter.updateData(newComments);
              commentsDiffResult.dispatchUpdatesTo(submissionCommentsAdapter);
            },
            logError("Error while streaming comments")
        );

    lifecycle().onPageCollapse()
        .startWith(LifecycleStreams.NOTHING)
        .takeUntil(lifecycle().onDestroy())
        .subscribe(o -> {
          contentLinkStream.accept(Optional.empty());
          contentImageView.setVisibility(View.GONE);
          contentVideoViewContainer.setVisibility(View.GONE);
          toolbarBackground.setSyncScrollEnabled(false);
        });

    // Swipe gestures.
    submissionCommentsAdapter.commentSwipeActionsProvider().setOnReplySwipeActionListener(parentCommentInfo -> {
      if (commentTreeConstructor.isCollapsed(parentCommentInfo) || !commentTreeConstructor.isReplyActiveFor(parentCommentInfo)) {
        commentTreeConstructor.showReplyAndExpandComments(parentCommentInfo);
        inlineReplyStream.accept(parentCommentInfo);
      } else {
        Keyboards.hide(getContext(), commentRecyclerView);
        commentTreeConstructor.hideReply(parentCommentInfo);
      }
    });
    commentRecyclerView.addOnItemTouchListener(new RecyclerSwipeListener(commentRecyclerView));
    commentRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

    // Inline reply additions.
    // Wait till the reply's View is added to the list and show keyboard.
    inlineReplyStream
        .doOnNext(o -> Timber.i("-----------------------------"))
        .switchMapSingle(parentContribution -> scrollToNewlyAddedReplyIfHidden(parentContribution).toSingleDefault(parentContribution))
        .switchMap(parentContribution -> showKeyboardWhenReplyIsVisible(parentContribution))
        .takeUntil(lifecycle().onDestroy())
        .subscribe();

    // Manually dispose reply draft subscribers, because Adapter#onViewHolderRecycled()
    // doesn't get called if the Activity is getting recreated.
    lifecycle().onDestroy()
        .take(1)
        .subscribe(o -> submissionCommentsAdapter.forceDisposeDraftSubscribers());

    // Reply discards.
    submissionCommentsAdapter.streamReplyDiscardClicks()
        .takeUntil(lifecycle().onDestroy())
        .subscribe(discardEvent -> {
          Keyboards.hide(getContext(), commentRecyclerView);
          commentTreeConstructor.hideReply(discardEvent.parentContribution());
        });

    // Reply GIF clicks.
    submissionCommentsAdapter.streamReplyGifClicks()
        .takeUntil(lifecycle().onDestroy())
        .subscribe(clickEvent -> {
          Activity activity = (Activity) getContext();
          activity.startActivityForResult(GiphyPickerActivity.intentWithPayload(getContext(), clickEvent), REQUEST_CODE_PICK_GIF);
        });

    // So it appears that SubredditActivity always gets recreated even when GiphyActivity is in foreground.
    // So when the activity result arrives, this Rx chain should be ready and listening.
    lifecycle().onActivityResults()
        .filter(result -> result.requestCode() == REQUEST_CODE_PICK_GIF && result.isResultOk())
        .takeUntil(lifecycle().onDestroy())
        .subscribe(result -> {
          ReplyInsertGifClickEvent gifInsertClickEvent = GiphyPickerActivity.extractExtraPayload(result.data());
          RecyclerView.ViewHolder holder = commentRecyclerView.findViewHolderForItemId(gifInsertClickEvent.replyRowItemId());
          if (holder == null) {
            Timber.e(new IllegalStateException("Couldn't find InlineReplyViewHolder after GIPHY activity result"));
            return;
          }

          GiphyGif pickedGiphyGif = GiphyPickerActivity.extractPickedGif(result.data());
          ((SubmissionCommentInlineReply.ViewHolder) holder).handlePickedGiphyGif(pickedGiphyGif);
        });

    // Reply fullscreen clicks.
    submissionCommentsAdapter.streamReplyFullscreenClicks()
        .takeUntil(lifecycle().onDestroy())
        .subscribe(fullscreenClickEvent -> {
          Bundle extraPayload = new Bundle();
          extraPayload.putLong(KEY_INLINE_REPLY_ROW_ID, fullscreenClickEvent.replyRowItemId());

          ComposeStartOptions startOptions = ComposeStartOptions.builder()
              .secondPartyName(fullscreenClickEvent.authorNameIfComment())
              .parentContribution(fullscreenClickEvent.parentContribution())
              .draftKey(fullscreenClickEvent.parentContribution())
              .preFilledText(fullscreenClickEvent.replyMessage())
              .extras(extraPayload)
              .build();
          Activity activity = (Activity) getContext();
          activity.startActivityForResult(ComposeReplyActivity.intent(getContext(), startOptions), REQUEST_CODE_FULLSCREEN_REPLY);
        });

    // Fullscreen reply results.
    Relay<ReplySendClickEvent> fullscreenReplySendStream = BehaviorRelay.create();
    lifecycle().onActivityResults()
        .filter(activityResult -> activityResult.requestCode() == REQUEST_CODE_FULLSCREEN_REPLY && activityResult.isResultOk())
        .map(activityResult -> ComposeReplyActivity.extractActivityResult(activityResult.data()))
        .doOnNext(composeResult -> {
          //noinspection ConstantConditions
          long inlineReplyRowId = composeResult.extras().getLong(KEY_INLINE_REPLY_ROW_ID);
          RecyclerView.ViewHolder holder = commentRecyclerView.findViewHolderForItemId(inlineReplyRowId);
          if (holder != null) {
            // Inline replies try saving message body to drafts when they're getting dismissed,
            // and because the dismissal happens asynchronously by RecyclerView, it often happens
            // after the draft has already been removed and sent, resulting in the message getting
            // re-saved as a draft. We'll manually disable saving of drafts here to solve that.
            //Timber.i("Disabling draft saving");
            ((SubmissionCommentInlineReply.ViewHolder) holder).setSavingDraftsAllowed(false);

          } else {
            Timber.e(new IllegalStateException("Couldn't find InlineReplyViewHolder after fullscreen reply result"));
          }
        })
        .map(composeResult -> ReplySendClickEvent.create(composeResult.optionalParentContribution().get(), composeResult.reply().toString()))
        .takeUntil(lifecycle().onDestroy())
        .subscribe(fullscreenReplySendStream);

    // Reply sends.
    submissionCommentsAdapter.streamReplySendClicks()
        .mergeWith(fullscreenReplySendStream)
        .takeUntil(lifecycle().onDestroy())
        .subscribe(sendClickEvent -> {
          // Posting to RecyclerView's message queue, because onActivityResult() gets called before
          // ComposeReplyActivity is able to exit and so keyboard doesn't get shown otherwise.
          commentRecyclerView.post(() -> Keyboards.hide(getContext(), commentRecyclerView));
          commentTreeConstructor.hideReply(sendClickEvent.parentContribution());

          // Message sending is not a part of the chain so that it does not get unsubscribed on destroy.
          // We're also removing the draft before sending it because even if the reply fails, it'll still
          // be present in the DB for the user to retry. Nothing will be lost.
          submissionStream
              .take(1)
              .flatMapCompletable(submission -> replyRepository.removeDraft(sendClickEvent.parentContribution())
                  //.doOnComplete(() -> Timber.i("Sending reply: %s", sendClickEvent.replyMessage()))
                  .andThen(replyRepository.sendReply(sendClickEvent.parentContribution(), ParentThread.of(submission), sendClickEvent.replyMessage()))
              )
              .compose(applySchedulersCompletable())
              .doOnError(e -> Timber.e(e))
              .subscribe(doNothingCompletable(), error -> RetryReplyJobService.scheduleRetry(getContext()));
        });

    // Reply retry-sends.
    submissionCommentsAdapter.streamReplyRetrySendClicks()
        .takeUntil(lifecycle().onDestroy())
        .subscribe(retrySendEvent -> {
          // Re-sending is not a part of the chain so that it does not get unsubscribed on destroy.
          replyRepository.reSendReply(retrySendEvent.failedPendingSyncReply())
              .compose(applySchedulersCompletable())
              .subscribe(doNothingCompletable(), error -> RetryReplyJobService.scheduleRetry(getContext()));
        });

    // Toggle collapse on comment clicks.
    submissionCommentsAdapter.streamCommentCollapseExpandEvents()
        .takeUntil(lifecycle().onDestroy())
        .subscribe(clickEvent -> {
          if (clickEvent.willCollapseOnClick()) {
            int firstCompletelyVisiblePos = ((LinearLayoutManager) commentRecyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
            boolean commentExtendsBeyondWindowTopEdge = firstCompletelyVisiblePos == -1 || clickEvent.commentRowPosition() < firstCompletelyVisiblePos;
            if (commentExtendsBeyondWindowTopEdge) {
              float viewTop = clickEvent.commentItemView().getY();
              commentRecyclerView.smoothScrollBy(0, (int) viewTop);
            }
          }

          commentTreeConstructor.toggleCollapse(clickEvent.commentInfo());
        });

    // Load-more-comment clicks.
    submissionCommentsAdapter.streamLoadMoreCommentsClicks()
        // This distinct() is important. Stupid JRAW inserts new comments directly into
        // the comment tree so if multiple API calls are made, it'll insert duplicate
        // items and result in a crash because RecyclerView expects stable IDs.
        .distinct()
        .concatMapEager(loadMoreClickEvent -> {
          if (loadMoreClickEvent.parentCommentNode().isThreadContinuation()) {
            return submissionRequestStream
                .take(1)
                .flatMap(submissionRequest -> {
                  DankSubmissionRequest continueThreadRequest = submissionRequest.toBuilder()
                      .focusComment(loadMoreClickEvent.parentCommentNode().getComment().getId())
                      .build();
                  Rect expandFromShape = Views.globalVisibleRect(loadMoreClickEvent.loadMoreItemView());
                  expandFromShape.top = expandFromShape.bottom;   // Because only expanding from a line is supported so far.
                  SubmissionPageLayoutActivity.start(getContext(), continueThreadRequest, expandFromShape);
                  return Observable.empty();
                });

          } else {
            return submissionRequestStream.zipWith(submissionStream, Pair::create)
                .take(1)
                .doOnNext(o -> commentTreeConstructor.setMoreCommentsLoading(loadMoreClickEvent.parentContribution(), true))
                .flatMapCompletable(pair -> {
                  DankSubmissionRequest submissionRequest = pair.first();
                  Submission submission = pair.second();
                  return submissionRepository.loadMoreComments(submission, submissionRequest, loadMoreClickEvent.parentCommentNode())
                      .subscribeOn(Schedulers.io())
                      .onErrorComplete();
                })
                .doOnTerminate(() -> commentTreeConstructor.setMoreCommentsLoading(loadMoreClickEvent.parentContribution(), false))
                .toObservable();
          }
        })
        .takeUntil(lifecycle().onDestroy())
        .subscribe(doNothing(), error -> {
          Timber.e(error, "Failed to load more comments");
          Toast.makeText(getContext(), R.string.submission_error_failed_to_load_more_comments, Toast.LENGTH_SHORT).show();
        });

    // Bottom-spacing for FAB.
    Views.executeOnMeasure(replyFAB, () -> {
      int spaceForFab = replyFAB.getHeight() + ((ViewGroup.MarginLayoutParams) replyFAB.getLayoutParams()).bottomMargin * 2;
      Views.setPaddingBottom(commentRecyclerView, spaceForFab);
    });
  }

  /**
   * Scroll to <var>parentContribution</var>'s reply if it's not going to be visible because it's located beyond the visible window.
   */
  @CheckResult
  private Completable scrollToNewlyAddedReplyIfHidden(PostedOrInFlightContribution parentContribution) {
    if (submissionStream.getValue().getFullName().equals(parentContribution.fullName())) {
      // Submission reply.
      return Completable.fromAction(() -> commentRecyclerView.smoothScrollToPosition(0));
    }

    return submissionCommentsAdapter.dataChanges()
        .take(1)
        .map(adapter -> adapter.getData())
        .map(newItems -> {
          int replyPosition = RecyclerView.NO_POSITION;
          for (int i = 0; i < newItems.size(); i++) {
            // Find the reply item's position.
            SubmissionScreenUiModel commentRow = newItems.get(i);
            //noinspection ConstantConditions Parent contribution's fullname cannot be null. Replies can only be made if they're non-null.
            if (commentRow instanceof SubmissionCommentInlineReply.UiModel
                && ((SubmissionCommentInlineReply.UiModel) commentRow).parentContribution().fullName().equals(parentContribution.fullName()))
            {
              replyPosition = i;
              break;
            }
          }
          return replyPosition;
        })
        .flatMapCompletable(replyPosition -> Completable.fromAction(() -> {
          RecyclerView.ViewHolder parentContributionItemVH = commentRecyclerView.findViewHolderForAdapterPosition(replyPosition - 1);
          int parentContributionBottom = parentContributionItemVH.itemView.getBottom() + commentListParentSheet.getTop();
          boolean isReplyHidden = parentContributionBottom >= submissionPageLayout.getBottom();

          // So this will not make the reply fully visible, but it'll be enough for the reply field
          // to receive focus which will in turn trigger the keyboard and push the field above in
          // the visible window.
          if (isReplyHidden) {
            int dy = parentContributionItemVH.itemView.getHeight();
            commentRecyclerView.smoothScrollBy(0, dy);
          }
        }));
  }

  /**
   * Wait for <var>parentContribution</var>'s reply View to bind and show keyboard once it's visible.
   */
  @CheckResult
  private Observable<ReplyItemViewBindEvent> showKeyboardWhenReplyIsVisible(PostedOrInFlightContribution parentContribution) {
    return submissionCommentsAdapter.streamReplyItemViewBinds()
        .filter(replyBindEvent -> {
          // This filter exists so that the keyboard is shown only for the target reply item
          // instead of another reply item that was found while scrolling to the target reply item.
          //noinspection ConstantConditions
          return replyBindEvent.uiModel().parentContribution().fullName().equals(parentContribution.fullName());
        })
        .take(1)
        .delay(COMMENT_LIST_ITEM_CHANGE_ANIM_DURATION, TimeUnit.MILLISECONDS)
        .observeOn(mainThread())
        .doOnNext(replyBindEvent -> commentRecyclerView.post(() -> Keyboards.show(replyBindEvent.replyField())));
  }

  private void setupContentImageView(View fragmentLayout) {
    Views.setMarginBottom(contentImageView, commentsSheetMinimumVisibleHeight);
    contentImageViewHolder = new SubmissionImageHolder(
        lifecycle(),
        fragmentLayout,
        contentLoadProgressView,
        submissionPageLayout,
        mediaHostRepository,
        deviceDisplayWidth
    );
  }

  private void setupContentVideoView() {
    contentVideoViewHolder = new SubmissionVideoHolder(
        contentVideoView,
        commentListParentSheet,
        contentLoadProgressView,
        submissionPageLayout,
        ExoPlayerManager.newInstance(lifecycle(), contentVideoView),
        mediaHostRepository,
        httpProxyCacheServer,
        deviceDisplayHeight,
        commentsSheetMinimumVisibleHeight
    );
  }

  private void setupCommentsSheet() {
    toolbarBackground.syncPositionWithSheet(commentListParentSheet);
    commentListParentSheet.setScrollingEnabled(false);
    contentLoadProgressView.syncPositionWithSheet(commentListParentSheet);
    contentLoadProgressView.setSyncScrollEnabled(true);

    Function0<Integer> mediaRevealDistanceFunc = () -> {
      // If the sheet cannot scroll up because the top-margin > sheet's peek distance, scroll it to 70%
      // of its height so that the user doesn't get confused upon not seeing the sheet scroll up.
      float mediaVisibleHeight = submissionContentStream.getValue().isImageOrGif()
          ? contentImageView.getVisibleZoomedImageHeight()
          : contentVideoViewContainer.getHeight();

      return (int) Math.min(
          commentListParentSheet.getHeight() * 8 / 10,
          mediaVisibleHeight - commentListParentSheet.getTop()
      );
    };

    // Toggle sheet's collapsed state on image click.
    contentImageView.setOnClickListener(o -> commentListParentSheet.smoothScrollTo(mediaRevealDistanceFunc.calculate()));

    // and on submission title click.
    submissionContentStream
        .ofType(MediaLink.class)
        .filter(o -> commentListParentSheet.isAtMaxScrollY())
        .switchMap(o -> submissionCommentsAdapter.streamHeaderClicks())
        .takeUntil(lifecycle().onDestroy())
        .subscribe(v -> commentListParentSheet.smoothScrollTo(mediaRevealDistanceFunc.calculate()));

    // Calculates if the top of the comment sheet is directly below the image.
    Function0<Boolean> isCommentSheetBeneathImageFunc = () -> {
      //noinspection CodeBlock2Expr
      return (int) commentListParentSheet.getY() == (int) contentImageView.getVisibleZoomedImageHeight();
    };

    contentImageView.getController().addOnStateChangeListener(new GestureController.OnStateChangeListener() {
      float lastZoom = contentImageView.getZoom();

      @Override
      public void onStateChanged(State state) {
        if (contentImageView.getDrawable() == null) {
          // Image isn't present yet. Ignore.
          return;
        }
        boolean isZoomingOut = lastZoom > state.getZoom();
        lastZoom = state.getZoom();

        // Scroll the comment sheet along with the image if it's zoomed in. This ensures that the sheet always sticks to the bottom of the image.
        int minimumGapWithBottom = 0;
        int contentHeightWithoutKeyboard = deviceDisplayHeight - minimumGapWithBottom - Views.statusBarHeight(contentVideoView.getResources());

        int boundedVisibleImageHeight = (int) Math.min(contentHeightWithoutKeyboard, contentImageView.getVisibleZoomedImageHeight());
        int boundedVisibleImageHeightMinusToolbar = boundedVisibleImageHeight - commentListParentSheet.getTop();
        commentListParentSheet.setMaxScrollY(boundedVisibleImageHeightMinusToolbar);

        if (isCommentSheetBeneathImage
            // This is a hacky workaround: when zooming out, the received callbacks are very discrete and
            // it becomes difficult to lock the comments sheet beneath the image.
            || (isZoomingOut && contentImageView.getVisibleZoomedImageHeight() <= commentListParentSheet.getY()))
        {
          commentListParentSheet.scrollTo(boundedVisibleImageHeightMinusToolbar);
        }
        isCommentSheetBeneathImage = isCommentSheetBeneathImageFunc.calculate();
      }

      @Override
      public void onStateReset(State oldState, State newState) {
      }
    });
    commentListParentSheet.addOnSheetScrollChangeListener(newScrollY ->
        isCommentSheetBeneathImage = isCommentSheetBeneathImageFunc.calculate()
    );
  }

  private void setupReplyFAB() {
    Relay<Boolean> fabSpaceAvailabilityChanges = BehaviorRelay.create();
    ScrollingRecyclerViewSheet.SheetScrollChangeListener sheetScrollChangeListener = sheetScrollY -> {
      // TODO.
      //float bylineBottom = submissionBylineView.getBottom() + sheetScrollY + commentListParentSheet.getTop();
      //boolean fabHasSpaceAvailable = bylineBottom < replyFAB.getTop();
      //fabSpaceAvailabilityChanges.accept(fabHasSpaceAvailable);
    };
    commentListParentSheet.addOnSheetScrollChangeListener(sheetScrollChangeListener);
    commentListParentSheet.post(() ->
        sheetScrollChangeListener.onScrollChange(commentListParentSheet.currentScrollY())  // Initial value.
    );

    // Show the FAB while the keyboard is hidden and there's space available.
    submissionStream
        .observeOn(mainThread())
        .doOnNext(o -> replyFAB.show())
        .switchMap(o -> Observable.combineLatest(keyboardVisibilityChangeStream, fabSpaceAvailabilityChanges,
            (keyboardVisibilityChangeEvent, spaceAvailable) -> !keyboardVisibilityChangeEvent.visible() && spaceAvailable)
        )
        .takeUntil(lifecycle().onDestroy())
        .subscribe(canShowReplyFAB -> {
          if (canShowReplyFAB) {
            replyFAB.show();
          } else {
            replyFAB.hide();
          }
        });

    RxView.clicks(replyFAB)
        .takeUntil(lifecycle().onDestroy())
        .withLatestFrom(submissionStream, (o, submission) -> submission)
        .map(submission -> PostedOrInFlightContribution.from(submission))
        .subscribe(submissionInfo -> {
          if (!userSessionRepository.isUserLoggedIn()) {
            getContext().startActivity(LoginActivity.intent(getContext()));
            return;
          }

          int firstVisiblePosition = ((LinearLayoutManager) commentRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
          boolean isSubmissionReplyVisible = firstVisiblePosition <= 1; // 1 == index of reply field.

          if (commentTreeConstructor.isReplyActiveFor(submissionInfo) && isSubmissionReplyVisible) {
            // Hide reply only if it's visible. Otherwise the user
            // won't understand why the reply FAB did not do anything.
            commentTreeConstructor.hideReply(submissionInfo);
          } else {
            commentTreeConstructor.showReply(submissionInfo);
            inlineReplyStream.accept(submissionInfo);
          }
        });
  }

  /**
   * Smoothly resize content when keyboard is shown or dismissed.
   */
  private void setupSoftInputModeChangesAnimation() {
    keyboardVisibilityChangeStream
        .takeUntil(lifecycle().onDestroy())
        .subscribe(new Consumer<KeyboardVisibilityChangeEvent>() {
          private ValueAnimator heightAnimator;
          private ViewGroup contentViewGroup;

          @Override
          public void accept(KeyboardVisibilityChangeEvent changeEvent) throws Exception {
            if (contentViewGroup == null) {
              //noinspection ConstantConditions
              contentViewGroup = ((Activity) getContext()).findViewById(Window.ID_ANDROID_CONTENT);
            }

            if (heightAnimator != null) {
              heightAnimator.cancel();
            }

            if (!commentListParentSheet.hasSheetReachedTheTop()) {
              // Bug workaround: when the sheet is not at the top, avoid smoothly animating the size change.
              // The sheet gets anyway pushed by the keyboard smoothly. Otherwise, only the list was getting
              // scrolled by the keyboard and the sheet wasn't receiving any nested scroll callbacks.
              Views.setHeight(contentViewGroup, changeEvent.contentHeightCurrent());
              return;
            }

            heightAnimator = ObjectAnimator.ofInt(changeEvent.contentHeightPrevious(), changeEvent.contentHeightCurrent());
            heightAnimator.addUpdateListener(animation -> Views.setHeight(contentViewGroup, (int) animation.getAnimatedValue()));
            heightAnimator.setInterpolator(Animations.INTERPOLATOR);
            heightAnimator.setDuration(ACTIVITY_CONTENT_RESIZE_ANIM_DURATION);
            heightAnimator.start();
          }
        });
  }

  private void setupStatusBarTint() {
    //noinspection ConstantConditions
    int defaultStatusBarColor = ContextCompat.getColor(getContext(), R.color.color_primary_dark);
    int statusBarHeight = Views.statusBarHeight(getResources());
    Observable<Bitmap> contentBitmapStream = Observable.merge(
        contentImageViewHolder.streamImageBitmaps(),
        contentVideoViewHolder.streamVideoFirstFrameBitmaps(statusBarHeight)
    );

    SubmissionStatusBarTintProvider statusBarTintProvider = new SubmissionStatusBarTintProvider(
        defaultStatusBarColor,
        statusBarHeight,
        deviceDisplayWidth
    );

    // Reset the toolbar icons' tint until the content is loaded.
    submissionContentStream
        .takeUntil(lifecycle().onDestroy())
        .subscribe(o -> toolbarCloseButton.setColorFilter(Color.WHITE));

    // For images and videos.
    statusBarTintProvider.streamStatusBarTintColor(contentBitmapStream, submissionPageLayout, commentListParentSheet)
        // Using switchMap() instead of delay() here so that any pending delay gets canceled in case a new tint is received.
        .switchMap(statusBarTint -> Observable.just(statusBarTint).delay(statusBarTint.delayedTransition() ? 100 : 0, TimeUnit.MILLISECONDS))
        .observeOn(mainThread())
        .takeUntil(lifecycle().onDestroy())
        .subscribe(new Consumer<StatusBarTint>() {
          public ValueAnimator tintChangeAnimator;

          @Override
          public void accept(StatusBarTint statusBarTint) throws Exception {
            if (tintChangeAnimator != null) {
              tintChangeAnimator.cancel();
            }

            Window window = ((Activity) getContext()).getWindow();
            tintChangeAnimator = ValueAnimator.ofArgb(window.getStatusBarColor(), statusBarTint.color());
            tintChangeAnimator.addUpdateListener(animation -> window.setStatusBarColor((int) animation.getAnimatedValue()));
            tintChangeAnimator.setDuration(150L);
            tintChangeAnimator.setInterpolator(Animations.INTERPOLATOR);
            tintChangeAnimator.start();

            // Set a light status bar on M+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              int flags = submissionPageLayout.getSystemUiVisibility();
              if (!statusBarTint.isDarkColor()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
              } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
              }
              submissionPageLayout.setSystemUiVisibility(flags);
            }

            // Use darker colors on light images.
            if (submissionPageLayout.getTranslationY() == 0f) {
              toolbarCloseButton.setColorFilter(statusBarTint.isDarkColor() ? Color.WHITE : Color.DKGRAY);
            }
          }
        }, logError("Wut?"));
  }

  /**
   * Update the submission to be shown. Since this Fragment is retained by {@link SubredditActivity},
   * we only update the UI everytime a new submission is to be shown.
   *
   * @param submissionRequest used for loading the comments of this submission.
   */
  public void populateUi(@Nullable Submission submission, DankSubmissionRequest submissionRequest) {
    // This will setup the title, byline and content immediately.
    if (submission != null) {
      submissionStream.accept(submission);
    }

    // This will load comments and then again update the title, byline and content.
    submissionRequestStream.accept(submissionRequest);

    if (submission != null) {
      unsubscribeOnCollapse(
          loadSubmissionContent(submission)
      );
    } else {
      // Wait till the submission is fetched before loading content.
      submissionStream
          .filter(fetchedSubmission -> fetchedSubmission.getId().equals(submissionRequest.id()))
          .take(1)
          .takeUntil(lifecycle().onDestroy())
          .subscribe(fetchedSubmission -> unsubscribeOnCollapse(
              loadSubmissionContent(fetchedSubmission)
          ));
    }
  }

  @CheckResult
  private Disposable loadSubmissionContent(Submission submission) {
    Relay<Object> retryLoadRequests = PublishRelay.create();

    // Collapse media-load-error link details View, if it's visible.
    retryLoadRequests
        .subscribe(o -> {
          Transition transitions = new TransitionSet()
              .addTransition(new ChangeBounds())
              .setInterpolator(Animations.INTERPOLATOR)
              .setOrdering(TransitionSet.ORDERING_TOGETHER)
              .setDuration(200);
          TransitionManager.endTransitions(submissionPageLayout);
          TransitionManager.beginDelayedTransition(submissionPageLayout, transitions);
        });

    return Single.fromCallable(() -> UrlParser.parse(submission.getUrl()))
        .subscribeOn(io())
        .observeOn(mainThread())
        .flatMapObservable(link -> retryLoadRequests.map(o -> link).startWith(link))
        .flatMapSingle(parsedLink -> {
          if (!(parsedLink instanceof UnresolvedMediaLink)) {
            return Single.just(parsedLink);
          }

          return mediaHostRepository.resolveActualLinkIfNeeded(((MediaLink) parsedLink))
              .subscribeOn(io())
              .doOnSubscribe(o -> {
                // Progress bar is later hidden in the subscribe() block.
                contentLoadProgressView.show();
              })
              .map((Link resolvedLink) -> {
                // Replace Imgur's cover image URL with reddit supplied URL, which will already be cached by Glide.
                if (resolvedLink instanceof ImgurAlbumLink) {
                  ImageWithMultipleVariants redditSuppliedImages = ImageWithMultipleVariants.of(submission.getThumbnails());
                  int albumContentLinkThumbnailWidth = SubmissionCommentsHeader.getWidthForAlbumContentLinkThumbnail(getContext());
                  String albumCoverImageUrl = redditSuppliedImages.findNearestFor(
                      albumContentLinkThumbnailWidth,
                      ((ImgurAlbumLink) resolvedLink).coverImageUrl()
                  );
                  return ((ImgurAlbumLink) resolvedLink).withCoverImageUrl(albumCoverImageUrl);
                }
                return resolvedLink;
              })
              .onErrorResumeNext(error -> {
                // Open this album in browser if Imgur rate limits have reached.
                if (error instanceof ImgurApiRequestRateLimitReachedException) {
                  return Single.just(ExternalLink.create(parsedLink.unparsedUrl()));
                } else {
                  handleMediaLoadError(error, retryLoadRequests);
                  contentLoadProgressView.hide();
                  return Single.never();
                }
              });
        })
        .observeOn(mainThread())
        .doOnNext(resolvedLink -> {
          contentImageView.setVisibility(resolvedLink.isImageOrGif() ? View.VISIBLE : View.GONE);
          contentVideoViewContainer.setVisibility(resolvedLink.isVideo() ? View.VISIBLE : View.GONE);

          // Show a shadow behind the toolbar because image/video submissions have a transparent toolbar.
          boolean transparentToolbar = resolvedLink.isImageOrGif() || resolvedLink.isVideo();
          toolbarBackground.setSyncScrollEnabled(transparentToolbar);
        })
        .takeUntil(lifecycle().onPageCollapseOrDestroy())
        .subscribe(
            resolvedLink -> {
              submissionContentStream.accept(resolvedLink);

              switch (resolvedLink.type()) {
                case SINGLE_IMAGE_OR_GIF:
                  Thumbnails redditSuppliedImages = submission.getThumbnails();

                  // Threading is handled internally by SubmissionImageHolder#load().
                  contentImageViewHolder.load((MediaLink) resolvedLink, redditSuppliedImages)
                      .ambWith(lifecycle().onPageCollapseOrDestroy().ignoreElements())
                      .subscribe(doNothingCompletable(), error -> handleMediaLoadError(error, retryLoadRequests));

                  // Open media in full-screen on click.
                  contentImageView.setOnClickListener(o -> urlRouter.forLink(((MediaLink) resolvedLink))
                      .withRedditSuppliedImages(submission.getThumbnails())
                      .open(getContext())
                  );

                  contentImageView.setContentDescription(getResources().getString(
                      R.string.cd_submission_image,
                      submission.getTitle()
                  ));
                  break;

                case REDDIT_PAGE:
                  contentLoadProgressView.hide();

                  // TODO.
                  if (submission.isSelfPost()) {
                  } else {
                    contentLinkStream.accept(Optional.of(resolvedLink));
                    //noinspection ConstantConditions
                    //unsubscribeOnCollapse(
                    //    linkDetailsViewHolder.populate(((RedditLink) resolvedLink))
                    //);
                    //linkDetailsView.setOnClickListener(o -> urlRouter.forLink(resolvedLink)
                    //    .expandFromBelowToolbar()
                    //    .open(getContext())
                    //);
                  }
                  break;

                case MEDIA_ALBUM:
                case EXTERNAL:
                  contentLoadProgressView.hide();
                  contentLinkStream.accept(Optional.of(resolvedLink));

                  // TODO.
                  //linkDetailsView.setOnClickListener(o -> urlRouter.forLink(resolvedLink)
                  //    .expandFromBelowToolbar()
                  //    .open(getContext())
                  //);

                  //if (resolvedLink instanceof ImgurAlbumLink) {
                  //  String redditSuppliedThumbnail = mediaHostRepository.findOptimizedQualityImageForDisplay(
                  //      submission.getThumbnails(),
                  //      linkDetailsViewHolder.getThumbnailWidthForExternalLink(),
                  //      ((ImgurAlbumLink) resolvedLink).coverImageUrl()
                  //  );
                  //  linkDetailsViewHolder.populate(((ImgurAlbumLink) resolvedLink), redditSuppliedThumbnail);
//
                  //} else {
                  //  String redditSuppliedThumbnail = mediaHostRepository.findOptimizedQualityImageForDisplay(
                  //      submission.getThumbnails(),
                  //      linkDetailsViewHolder.getThumbnailWidthForExternalLink(),
                  //      null
                  //  );
                  //  unsubscribeOnCollapse(
                  //      linkDetailsViewHolder.populate(((ExternalLink) resolvedLink), redditSuppliedThumbnail)
                  //  );
                  //}
                  break;

                case SINGLE_VIDEO:
                  userPreferences.streamHighResolutionMediaNetworkStrategy()
                      .flatMap(strategy -> networkStateListener.streamNetworkInternetCapability(strategy))
                      .firstOrError()
                      .flatMapCompletable(canLoadHighQualityVideo -> contentVideoViewHolder.load((MediaLink) resolvedLink, canLoadHighQualityVideo))
                      .ambWith(lifecycle().onPageCollapseOrDestroy().ignoreElements())
                      .subscribe(doNothingCompletable(), error -> handleMediaLoadError(error, retryLoadRequests));
                  break;

                default:
                  throw new UnsupportedOperationException("Unknown content: " + resolvedLink);
              }

            }, error -> Timber.e(error)
        );
  }

  private void handleMediaLoadError(Throwable error, Relay<Object> retryLoadRequests) {
    ResolvedError resolvedError = errorResolver.resolve(error);
    resolvedError.ifUnknown(() -> Timber.e(error));

    // TODO.
    //linkDetailsViewHolder.populateMediaLoadError(resolvedError);
    //linkDetailsView.setOnClickListener(o -> retryLoadRequests.accept(NOTHING));
  }

// ======== EXPANDABLE PAGE CALLBACKS ======== //

  /**
   * @param upwardPagePull True if the PAGE is being pulled upwards. Remember that upward pull == downward scroll and vice versa.
   * @return True to consume this touch event. False otherwise.
   */
  @Override
  public boolean onInterceptPullToCollapseGesture(MotionEvent event, float downX, float downY, boolean upwardPagePull) {
    if (touchLiesOn(commentListParentSheet, downX, downY)) {
      return upwardPagePull
          ? commentListParentSheet.canScrollUpwardsAnyFurther()
          : commentListParentSheet.canScrollDownwardsAnyFurther();
    } else {
      return touchLiesOn(contentImageView, downX, downY) && contentImageView.canPanFurtherVertically(upwardPagePull);
    }
  }

  @Override
  public void onPageAboutToExpand(long expandAnimDuration) {
  }

  @Override
  public void onPageExpanded() {
  }

  @Override
  public void onPageAboutToCollapse(long collapseAnimDuration) {
    Keyboards.hide(getContext(), commentRecyclerView);
  }

  @Override
  public void onPageCollapsed() {
    contentVideoViewHolder.pausePlayback();
    onCollapseSubscriptions.clear();
    commentTreeConstructor.reset();

    commentListParentSheet.scrollTo(0);
    commentListParentSheet.setScrollingEnabled(false);

    submissionCommentsAdapter.updateDataAndNotifyDatasetChanged(null);

    //noinspection ConstantConditions
    if (((Callbacks) getContext()).submissionPageAnimationOptimizer().isOptimizationPending()) {
      contentImageView.setVisibility(View.GONE);
      contentVideoViewContainer.setVisibility(View.GONE);
      toolbarBackground.setSyncScrollEnabled(false);
    }
  }

  @Deprecated
  private void unsubscribeOnCollapse(Disposable subscription) {
    onCollapseSubscriptions.add(subscription);
  }

  public SubmissionPageLifecycleStreams lifecycle() {
    return lifecycleStreams;
  }
}