import AppCommonApi
import SwiftUI

/// How a page stands before its first answer (ui/content-loading): progress, or what failed (`error`) with Retry. On a page of its own
/// it fills the page, the progress centred and the failure from the top; `inList`, as a list's status row after rows that stay (a
/// feed's filter bar, a board's choices), it spans the row with room above and below.
struct FirstLoadStatus: View {
    /// What failed, or nil while the first load runs.
    let error: UiError?
    var inList = false
    let retry: () -> Void

    var body: some View {
        if inList {
            Group {
                if let error {
                    ErrorMessage(error, retry: retry)
                } else {
                    AppProgress()
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            .listStatusRow()
        } else if let error {
            ErrorMessage(error, retry: retry)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            AppProgress().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

/// A loaded paged list's status row (ui/content-loading), above its items (a refresh) or below them (the next page): a small spinner,
/// or what failed with Retry, laid out as `layout` says.
struct PagedStatusRow: View {
    let status: PagedShown.Status
    var layout: ErrorMessage.Layout = .stacked
    let retry: () -> Void

    var body: some View {
        switch status.sealedType() {
        case .progress:
            AppProgress(small: true).frame(maxWidth: .infinity).listStatusRow()
        case .failure(let failure):
            ErrorMessage(failure.value.error, layout: layout, retry: retry).frame(maxWidth: .infinity).listStatusRow()
        }
    }
}

extension PagedShown.Status {
    /// What failed, or nil while it loads.
    var failedError: UiError? {
        if case .failure(let failure) = sealedType() { failure.value.error } else { nil }
    }
}
