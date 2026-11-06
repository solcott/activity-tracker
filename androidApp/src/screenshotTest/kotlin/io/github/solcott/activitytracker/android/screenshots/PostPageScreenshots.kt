package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.post.PostPageSamples
import io.github.solcott.activitytracker.app.post.PostPageState
import io.github.solcott.activitytracker.app.post.PostPageUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun PostPageLoaded() {
    ScreenPreview { PostPageUi(PostPageSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun PostPageStates(@PreviewParameter(PostPageStates::class) state: PostPageState) {
    ScreenPreview { PostPageUi(state) }
}

class PostPageStates : OtherSamples<PostPageState>(PostPageSamples.all + mapOf("likers" to PostPageSamples.likers))
