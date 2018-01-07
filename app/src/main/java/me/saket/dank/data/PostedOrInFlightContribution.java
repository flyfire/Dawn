package me.saket.dank.data;

import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import net.dean.jraw.models.Message;
import net.dean.jraw.models.Thing;
import net.dean.jraw.models.VoteDirection;
import net.dean.jraw.models.attr.Votable;

import me.saket.dank.ui.submission.CommentTreeConstructor;
import me.saket.dank.ui.submission.PendingSyncReply;

/**
 * A contribution that is either present on remote or is still being posted.
 * Could be a submission, a comment or a message.
 */
public interface PostedOrInFlightContribution extends Parcelable {

  enum State {
    /**
     * Either received from the server or posted locally and synced with remote.
     * The full-name is available in this state.
     */
    POSTED,

    /**
     * Yet to be sent to remote. Full-name isn't available yet
     */
    IN_FLIGHT
  }

  /** Null only when {@link #isPosted()} returns false. */
  @Nullable
  String fullName();

  Integer score();

  VoteDirection voteDirection();

  /**
   * When posted, gestures can be registered, replies can be made, etc.
   */
  boolean isPosted();

  /**
   * Used by {@link CommentTreeConstructor} as this object's ID.
   */
  String idForTogglingCollapse();

  static <T extends Thing & Votable> PostedOrInFlightContribution from(T votableThing) {
    return ContributionFetchedFromRemote.create(votableThing.getFullName(), votableThing.getScore(), votableThing.getVote());
  }

  static PostedOrInFlightContribution createRemote(String fullName, Integer score, VoteDirection vote) {
    return ContributionFetchedFromRemote.create(fullName, score, vote);
  }

  static PostedOrInFlightContribution from(Message message) {
    return MessageFetchedFromRemote.create(message.getFullName());
  }

  static PostedOrInFlightContribution createLocal(PendingSyncReply pendingSyncReply) {
    State state = pendingSyncReply.state() == PendingSyncReply.State.POSTED
        ? State.POSTED
        : State.IN_FLIGHT;

    String idForTogglingCollapse = pendingSyncReply.parentContributionFullName() + "_reply_" + pendingSyncReply.createdTimeMillis();
    String fullName = pendingSyncReply.postedFullName();
    return LocallyPostedContribution.create(fullName, 1, VoteDirection.UPVOTE, state, idForTogglingCollapse);
  }

  @AutoValue
  abstract class ContributionFetchedFromRemote implements PostedOrInFlightContribution {
    // Overriding just to make this non-null.
    @NonNull
    @Override
    public abstract String fullName();

    @Override
    public boolean isPosted() {
      return true;
    }

    @Override
    public String idForTogglingCollapse() {
      return fullName();
    }

    public static ContributionFetchedFromRemote create(String fullName, Integer score, VoteDirection voteDirection) {
      return new AutoValue_PostedOrInFlightContribution_ContributionFetchedFromRemote(score, voteDirection, fullName);
    }

    public static JsonAdapter<ContributionFetchedFromRemote> jsonAdapter(Moshi moshi) {
      return new AutoValue_PostedOrInFlightContribution_ContributionFetchedFromRemote.MoshiJsonAdapter(moshi);
    }
  }

  @AutoValue
  abstract class LocallyPostedContribution implements PostedOrInFlightContribution {

    public abstract State state();

    public abstract String idForTogglingCollapse();

    public boolean isPosted() {
      return state() == State.POSTED;
    }

    public static LocallyPostedContribution create(
        @Nullable String fullName,
        Integer score,
        VoteDirection voteDirection,
        State state,
        String idForTogglingCollapse)
    {
      return new AutoValue_PostedOrInFlightContribution_LocallyPostedContribution(
          fullName,
          score,
          voteDirection,
          state,
          idForTogglingCollapse
      );
    }

    public static JsonAdapter<LocallyPostedContribution> jsonAdapter(Moshi moshi) {
      return new AutoValue_PostedOrInFlightContribution_LocallyPostedContribution.MoshiJsonAdapter(moshi);
    }
  }

  @AutoValue
  abstract class MessageFetchedFromRemote implements PostedOrInFlightContribution {
    @Override
    public VoteDirection voteDirection() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Integer score() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String idForTogglingCollapse() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPosted() {
      return true;
    }

    public static MessageFetchedFromRemote create(String fullName) {
      return new AutoValue_PostedOrInFlightContribution_MessageFetchedFromRemote(fullName);
    }
  }
}
