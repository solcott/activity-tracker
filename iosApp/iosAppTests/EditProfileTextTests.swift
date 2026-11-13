import Foundation
import Testing
import UIKit

@testable import ActivityTracker

/// What edit profile says, counts and offers (profile/private-details, dev/shared-display-text). The rules are Kotlin's, with their own
/// tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared form does.
@MainActor
struct EditProfileTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func theLoadedFormIsFilledWithCounters() throws {
        let state = try #require(Samples.editProfile["loaded"])
        #expect(String(localized: .editProfileTitle) == "Edit profile")
        #expect(state.displayName.value == "Alice")
        // `description` doesn't reach Swift; `bio` is the same field.
        #expect(state.bio.value == "Trail runner and weekend rider.")
        #expect(read(state.displayNameCounter) == "5/64")
        #expect(read(state.descriptionCounter) == "31/256")
        #expect(state.loaded)
        #expect(state.canSave)
        #expect(state.signInAgainNotice == nil)
    }

    @Test func eachGroupSaysWhoMaySeeIt() throws {
        let state = try #require(Samples.editProfile["loaded"])
        #expect(read(LabelText.shared.detailGroup(group: .Location)) == "Location")
        #expect(read(LabelText.shared.visibilityScope(group: .Location)) == "Who can see your city, state and country")
        #expect(read(LabelText.shared.visibility(visibility: state.visibilityOf(group: .Location))) == "Public")
        #expect(read(LabelText.shared.visibility(visibility: state.visibilityOf(group: .BirthDate))) == "Only me")
        #expect(read(LabelText.shared.visibility(visibility: state.visibilityOf(group: .Body))) == "Mutuals")
        #expect(state.visibilityOptions.elements(as: String.self).count == 4)
        #expect(
            read(state.followersNote(group: .Gender))
                == "Anyone who follows you can see what you share with Followers. Turn on Approve followers in your profile to choose who.")
        #expect(state.followersNote(group: .Location) == nil)
        #expect(try #require(Samples.editProfileVariants["approving"]).followersNote(group: .Gender) == nil)
    }

    @Test func aHiddenPublicDetailIsWarnedOf() throws {
        let state = try #require(Samples.editProfileVariants["nowHidden"])
        #expect(state.isNowHidden(group: .Location))
        #expect(!state.isNowHidden(group: .Gender))
        #expect(
            read(EditProfileTexts.shared.nowHidden(group: .Location))
                == "Location: public until now. Hiding removes it from your profile, but anyone may already have a copy.")
    }

    @Test func unitsLabelHeightAndWeight() throws {
        let metric = try #require(Samples.editProfile["loaded"])
        #expect(read(metric.heightLabel) == "Height (cm)")
        #expect(read(metric.weightLabel) == "Weight (kg)")
        #expect(!metric.heightInFeetAndInches)
        let imperial = try #require(Samples.editProfileVariants["imperial"])
        #expect(read(imperial.heightLabel) == "Height (ft)")
        #expect(read(EditProfileTexts.shared.inchesRow) == "Height (in)")
        #expect(read(imperial.weightLabel) == "Weight (lb)")
        #expect(imperial.heightInFeetAndInches)
        #expect(imperial.heightValid)
    }

    @Test func aWeightThatIsntOneCantBeSaved() throws {
        let state = try #require(Samples.editProfile["invalid"])
        #expect(!state.weightValid)
        #expect(!state.canSave)
        #expect(!(try #require(Samples.editProfileVariants["saving"])).canSave)
    }

    @Test func theBirthdateIsADay() throws {
        let loaded = try #require(Samples.editProfile["loaded"])
        let day = try #require(loaded.birthDay)
        #expect(CalendarDay(day) == CalendarDay(year: 1990, month: 4, day: 12))
        #expect(try #require(Samples.editProfileVariants["imperial"]).birthDay == nil)
        #expect(EditProfileEvent.BirthDateChanged(date: CalendarDay(year: 1990, month: 4, day: 13).localDate).value == "1990-04-13")
        #expect(String(localized: .editProfileBirthDateChoose) == "Choose birthdate")
    }

    @Test func theBirthdateReadsWrittenOut() throws {
        let loaded = try #require(Samples.editProfile["loaded"])
        #expect(AppStrings.string(try #require(EditProfileTexts.shared.birthDate(state: loaded))) == "Apr 12, 1990")
        #expect(EditProfileTexts.shared.birthDate(state: try #require(Samples.editProfileVariants["imperial"])) == nil)
    }

    @Test func genderOffersTheSharedChoices() throws {
        let choices = GenderChoices.shared
        #expect(choices.all.map { read(choices.label(choice: $0)) } == ["Not set", "Female", "Male", "Non-binary", "Self-describe"])
        let imperial = try #require(Samples.editProfileVariants["imperial"])
        #expect(choices.chosen(gender: imperial.gender.value, chose: false) == choices.SELF_DESCRIBE)
        #expect(choices.selfDescribes(gender: imperial.gender.value, chose: false))
        #expect(String(localized: .editProfileGenderDescribe) == "How you describe your gender")
    }

    @Test func theNoticesAreTheSharedOnes() throws {
        let failedState = try #require(Samples.editProfileVariants["saveFailed"])
        let failed = try #require(failedState.signInAgainNotice)
        #expect(!failed.offersSignInAgain)
        let signInState = try #require(Samples.editProfileVariants["needsSignIn"])
        let signIn = try #require(signInState.signInAgainNotice)
        #expect(signIn.offersSignInAgain)
        #expect(String(localized: .actionSignInAgain) == "Sign in again")
    }

    @Test func thePhotoIsSavedPickedOrNone() throws {
        #expect((try #require(Samples.editProfileVariants["savedPhoto"]).avatar as? AvatarImage.Saved)?.url == "https://example.test/alice.jpg")
        let pickedState = try #require(Samples.editProfileVariants["pickedPhoto"])
        let picked = try #require(pickedState.avatar as? AvatarImage.Picked)
        #expect(UIImage(data: picked.jpeg.data) != nil)
        #expect(try #require(Samples.editProfile["loaded"]).avatar == nil)
        #expect(String(localized: .editProfilePhotoChange) == "Change photo")
        #expect(String(localized: .editProfilePhotoRemove) == "Remove photo")
        #expect(EditProfileLimits.shared.avatarPixels == 512)
    }
}
