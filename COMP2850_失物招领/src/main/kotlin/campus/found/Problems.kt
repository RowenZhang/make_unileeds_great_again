package campus.found

import io.ktor.http.HttpStatusCode

enum class Problem(
    val code: String,
    val status: HttpStatusCode,
    val message: String,
    val field: String? = null,
) {
    NAME(
        "LF01",
        HttpStatusCode.UnprocessableEntity,
        "Enter an item name of 1–100 characters.",
        "name",
    ),
    DATE(
        "LF02",
        HttpStatusCode.UnprocessableEntity,
        "Choose a valid date in YYYY-MM-DD format.",
        "date",
    ),
    FUTURE(
        "LF03",
        HttpStatusCode.UnprocessableEntity,
        "The date found cannot be later than today in Leeds.",
        "date",
    ),
    TAGS(
        "LF04",
        HttpStatusCode.UnprocessableEntity,
        "Use up to 8 tags, with 24 characters or fewer per tag.",
        "tags",
    ),
    PLACE(
        "LF05",
        HttpStatusCode.UnprocessableEntity,
        "Use up to 120 characters for the collection point.",
        "place",
    ),
    PHOTO_TYPE("LF06", HttpStatusCode.UnsupportedMediaType, "Choose a PNG or JPEG image.", "photo"),
    PHOTO_SIZE(
        "LF07",
        HttpStatusCode.PayloadTooLarge,
        "Choose an image no larger than 5 MiB.",
        "photo",
    ),
    PHOTO_DATA(
        "LF08",
        HttpStatusCode.UnprocessableEntity,
        "The photo could not be read. Export it as a PNG or JPEG and try again.",
        "photo",
    ),
    PHOTO_DIMENSIONS(
        "LF09",
        HttpStatusCode.UnprocessableEntity,
        "Choose a photo of up to 16 megapixels and 8,192 pixels per side.",
        "photo",
    ),
    QUERY("LF10", HttpStatusCode.BadRequest, "Search using 100 characters or fewer.", "q"),
    MISSING("LF11", HttpStatusCode.NotFound, "This item could not be found."),
    NO_PHOTO("LF12", HttpStatusCode.NotFound, "This item has no photo."),
    FORM(
        "LF13",
        HttpStatusCode.BadRequest,
        "The form could not be read. Reopen the upload form and try again.",
    ),
    BODY(
        "LF14",
        HttpStatusCode.PayloadTooLarge,
        "The upload is too large. Choose a photo of up to 5 MiB.",
    ),
    STORAGE(
        "LF15",
        HttpStatusCode.ServiceUnavailable,
        "The database is temporarily unavailable. Try again in a moment.",
    ),
    INTERNAL(
        "LF16",
        HttpStatusCode.InternalServerError,
        "Something went wrong while processing this request.",
    ),
    ORIGIN(
        "LF17",
        HttpStatusCode.Forbidden,
        "Open the form on this site and try again.",
    ),
    PAGE("LF18", HttpStatusCode.BadRequest, "Choose a valid results page."),
    ROUTE("LF19", HttpStatusCode.NotFound, "This page could not be found."),
    DELETE_CONFIRMATION(
        "LF20",
        HttpStatusCode.Forbidden,
        "Reopen this item's deletion page and confirm again.",
    ),
}

class InputProblem(val problem: Problem) : RuntimeException(problem.code)
