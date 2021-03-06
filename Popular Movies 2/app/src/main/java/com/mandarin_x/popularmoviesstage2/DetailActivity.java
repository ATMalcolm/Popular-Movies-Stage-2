package com.mandarin_x.popularmoviesstage2;

import com.squareup.picasso.Picasso;
import com.mandarin_x.popularmoviesstage2.data.FavoritesContract;
import com.mandarin_x.popularmoviesstage2.databinding.ActivityDetailBinding;
import com.mandarin_x.popularmoviesstage2.models.Movie;
import com.mandarin_x.popularmoviesstage2.models.Review;
import com.mandarin_x.popularmoviesstage2.models.Trailer;
import com.mandarin_x.popularmoviesstage2.utilities.NetworkUtils;
import com.mandarin_x.popularmoviesstage2.utilities.TmdbJsonUtils;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends AppCompatActivity implements TrailerAdapter.TrailerAdapterOnClickHandler {
    private static final String TAG = DetailActivity.class.getSimpleName();
    private static final String YOUTUBE_VIDEO_BASE_URL = "https://www.youtube.com/watch?v=";
    ActivityDetailBinding mBinding;
    private RecyclerView mTrailersRecyclerView;
    private com.mandarin_x.popularmoviesstage2.TrailerAdapter mTrailerAdapter;
    private RecyclerView mReviewsRecyclerView;
    private com.mandarin_x.popularmoviesstage2.ReviewAdapter mReviewAdapter;
    private ProgressBar pbLoadingIndicator;
    private FloatingActionButton fab;
    private boolean favorited = false;
    private String sortOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_detail);

        // set up recyclerview and adapter to display the trailers
        mTrailersRecyclerView = mBinding.rvTrailers;
        LinearLayoutManager trailersLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mTrailersRecyclerView.setLayoutManager(trailersLayoutManager);
        mTrailerAdapter = new com.mandarin_x.popularmoviesstage2.TrailerAdapter(this);
        mTrailersRecyclerView.setAdapter(mTrailerAdapter);

        // set up recyclerview and adapter to display the reviews
        mReviewsRecyclerView = mBinding.rvReviews;
        LinearLayoutManager reviewsLayoutManager = new LinearLayoutManager(this);
        mReviewsRecyclerView.setLayoutManager(reviewsLayoutManager);
        mReviewAdapter = new com.mandarin_x.popularmoviesstage2.ReviewAdapter();
        mReviewsRecyclerView.setAdapter(mReviewAdapter);

        TextView tvTitle = mBinding.tvTitle;
        ImageView ivThumbnail = mBinding.ivThumbnail;
        TextView tvOverview = mBinding.tvOverview;
        TextView tvRating = mBinding.tvRating;
        fab = mBinding.fab;
        pbLoadingIndicator = mBinding.pbDetailLoadingIndicator;

        Intent callingIntent = getIntent();

        if (callingIntent.hasExtra("movie")) {
            final Movie movie = callingIntent.getParcelableExtra("movie");
            String postersBaseUrl = callingIntent.getStringExtra("posters_base_url");
            String posterWidth = callingIntent.getStringExtra("poster_width");
            sortOrder = callingIntent.getStringExtra("sortorder");
            // if the movie has been favorited then show the unfavorite button instead of favorite
            if (sortOrder.equals(MainActivity.SORT_ORDER_FAVORITES) || isFavorited(movie.getId())) {
                favorited = true;
            }
            setFabIcon(favorited);

            String titleAndYear = String.format(getString(R.string.movie_title_and_year), movie.getTitle(), movie.getReleaseDate().substring(0, 4));
            String rating = String.format(getString(R.string.movie_rating), movie.getUserRating());
            String id = movie.getId();
            String tmdbApiKey = callingIntent.getStringExtra("tmdb_api_key");
            tvTitle.setText(titleAndYear);
            tvRating.setText(rating);
            // Picasso caches images, so it should not cause extra work/bandwidth to give it the
            // same poster URL used in the main activity
            Picasso.with(this).load(postersBaseUrl + posterWidth + movie.getPosterUrl()).into(ivThumbnail);
            tvOverview.setText(movie.getOverview());
            loadTrailersAndReviews(id, tmdbApiKey);

            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (favorited) {
                        // if they're unfavoriting a favorite, delete it from the favorites database
                        String mSelectionClause = FavoritesContract.Favorite.COLUMN_MOVIE_ID + " = ?";
                        String[] mSelectionArgs = {movie.getId()};
                        int mRowsDeleted = getContentResolver().delete(FavoritesContract.Favorite.CONTENT_URI, mSelectionClause, mSelectionArgs);
                        Toast.makeText(DetailActivity.this, movie.getTitle() + DetailActivity.this.getString(R.string.unfavorited), Toast.LENGTH_SHORT).show();
                    } else {
                        new setMovieAsFavorite().execute(movie);
                    }
                    favorited = !favorited;
                    setFabIcon(favorited);
                }
            });
        }
    }

    private boolean isFavorited(String movieId) {
        // Has the movie already been favorited?
        String mSelectionClause = FavoritesContract.Favorite.COLUMN_MOVIE_ID + " = ?";
        String[] mSelectionArgs = {movieId};
        Cursor mCursor = getContentResolver().query(FavoritesContract.Favorite.CONTENT_URI, null, mSelectionClause, mSelectionArgs, null);                       // The sort order for the returned rows
        boolean movieIsFavorited = (mCursor != null && mCursor.getCount() == 1);
        mCursor.close();
        return movieIsFavorited;
    }

    private void setFabIcon(boolean favorited) {
        if (favorited) {
            fab.setImageResource(R.drawable.ic_unfavorite);
        } else {
            fab.setImageResource(R.drawable.ic_mark_favorite);
        }
    }

    private void loadTrailersAndReviews(String movieId, String apiKey) {
        new FetchTrailersAndReviewsTask().execute(movieId, apiKey, null);
    }

    @Override
    public void onClick(Trailer trailer) {
        // tapping a trailer thumbnail brings up the trailer
        Uri trailerUri = Uri.parse(YOUTUBE_VIDEO_BASE_URL + trailer.getKey());
        Intent intent = new Intent(Intent.ACTION_VIEW, trailerUri);
        // make sure the user has a web browser or YouTube client installed
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    public class setMovieAsFavorite extends AsyncTask<Movie, Void, Uri> {
        Movie movie;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Uri doInBackground(Movie... params) {
            // Insert new favorite data via a ContentResolver
            // Create new empty ContentValues object
            movie = params[0];
            ContentValues contentValues = new ContentValues();
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_TITLE, movie.getTitle());
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_OVERVIEW, movie.getOverview());
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_POSTER_URL, movie.getPosterUrl());
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_USER_RATING, movie.getUserRating());
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_RELEASE_DATE, movie.getReleaseDate());
            contentValues.put(FavoritesContract.Favorite.COLUMN_MOVIE_ID, movie.getId());
            // Insert the content values via a ContentResolver
            return getContentResolver().insert(FavoritesContract.Favorite.CONTENT_URI, contentValues);
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (uri != null) {
                Toast.makeText(DetailActivity.this, movie.getTitle() + DetailActivity.this.getString(R.string.favorited), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // using a background task, get the reviews and trailers data and display it
    public class FetchTrailersAndReviewsTask extends AsyncTask<String, String, List<Object>> {//Movie[]> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pbLoadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<Object> doInBackground(String... params) {
            URL trailersAndReviewsRequestUrl = NetworkUtils.buildTrailersAndReviewsUrl(params[0], params[1]);
            try {
                String jsonTmdbResponse = NetworkUtils.getResponseFromHttpUrl(trailersAndReviewsRequestUrl);
                Review[] reviewsFromJson = TmdbJsonUtils.getReviewsFromJson(DetailActivity.this, jsonTmdbResponse);
                Trailer[] trailersFromJson = TmdbJsonUtils.getTrailersFromJson(DetailActivity.this, jsonTmdbResponse);

                List<Object> reviewsAndTrailers = new ArrayList<Object>();
                reviewsAndTrailers.add(reviewsFromJson);
                reviewsAndTrailers.add(trailersFromJson);

                return reviewsAndTrailers;

            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, (String) getText(R.string.error_message));
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Object> reviewsAndTrailers) {
            if (reviewsAndTrailers != null) {
                Review[] reviews = (Review[]) reviewsAndTrailers.get(0);
                mReviewAdapter.setReviewData(reviews);

                Trailer[] trailers = (Trailer[]) reviewsAndTrailers.get(1);
                mTrailerAdapter.setTrailerData(trailers);

            } else {
                Toast.makeText(DetailActivity.this, R.string.no_data_received, Toast.LENGTH_LONG).show();
            }
            pbLoadingIndicator.setVisibility(View.INVISIBLE);
        }
    }

}
