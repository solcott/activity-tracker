import PhotosUI
import SwiftUI

/// Edit profile (profile/private-details), drawn from the states `EditProfilePresenter` publishes: the photo, the display name and bio,
/// Approve followers, and each detail group with who may see it. What it says, counts, offers and allows is Kotlin's (`EditProfileState`'s
/// getters, `EditProfileTexts`, `GenderChoices`, `CountrySearch`), shared with the Compose form. Save is in the bar, and a failed save or
/// the need to sign in again shows at the top. It is pushed over the athlete's own profile.
struct EditProfileView: View {
    let state: EditProfileState

    @State private var displayName: TextFieldModel
    @State private var bio: TextFieldModel
    @State private var locality: TextFieldModel
    @State private var region: TextFieldModel
    @State private var gender: TextFieldModel
    @State private var weight: TextFieldModel
    @State private var height: TextFieldModel
    @State private var heightInches: TextFieldModel
    /// Self-describe was chosen and no known gender since, so its field stays while it is empty.
    @State private var choseSelfDescribe = false
    @State private var pickingBirthDate = false
    @State private var choosingCountry = false
    @State private var photoItem: PhotosPickerItem?
    @State private var countries = CountryNames()
    @Environment(\.calendar) private var calendar

    init(state: EditProfileState) {
        self.state = state
        // No rule refuses text past a limit: as on the shared form, the counter turns red and Save waits, so a saved value that is
        // already too long can still be shortened.
        _displayName = State(initialValue: TextFieldModel(state.displayName))
        _bio = State(initialValue: TextFieldModel(state.bio))
        _locality = State(initialValue: TextFieldModel(state.locality))
        _region = State(initialValue: TextFieldModel(state.region))
        _gender = State(initialValue: TextFieldModel(state.gender))
        _weight = State(initialValue: TextFieldModel(state.weight))
        _height = State(initialValue: TextFieldModel(state.height))
        _heightInches = State(initialValue: TextFieldModel(state.heightInches))
    }

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadProfile, retry: { send(EditProfileEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(.editProfileTitle)) {
            if state.loaded {
                // Read here so typing, which changes no Kotlin state SwiftUI sees, redraws whether Save can be used.
                let _ = typed
                Button(.actionSave) { send(EditProfileEvent.Save.shared) }
                    .fontWeight(.semibold)
                    .disabled(!state.canSave)
            }
        }
        .sheet(isPresented: $choosingCountry) {
            CountrySheet(countries: countries, chosen: state.country) { send(EditProfileEvent.CountryChanged(value: $0)) }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            photoItem = nil
            Task {
                await AvatarPick.avatar.pick({ try await item.loadTransferable(type: Data.self) }) { jpeg in
                    send(EditProfileEvent.AvatarPicked(jpeg: jpeg))
                }
            }
        }
    }

    /// Every field's text as typed, so a view reading it redraws on each keystroke.
    private var typed: [String] {
        [displayName, bio, locality, region, gender, weight, height, heightInches].map(\.text)
    }

    private var form: some View {
        let _ = typed
        return Form {
            // Why the save failed, or that the session must sign in again, where it is seen with Save in the bar.
            if let notice = state.signInAgainNotice {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        ErrorMessage(notice.error, layout: .row)
                        if notice.offersSignInAgain {
                            AppSecondaryButton(.actionSignInAgain) { send(EditProfileEvent.SignInAgain.shared) }
                        }
                    }
                }
            }
            Section { photo }
            Section {
                AppTextField(
                    label: .editProfileDisplayName, model: displayName, state: state.displayName, layout: .row,
                    options: TextFieldOptions(capitalization: .words), supportingText: AppStrings.string(state.displayNameCounter),
                    isError: !state.displayNameValid)
                AppTextField(
                    label: .editProfileDescription, model: bio, state: state.bio, layout: .multiline,
                    supportingText: AppStrings.string(state.descriptionCounter), isError: !state.descriptionValid)
            }
            Section {
                Toggle(
                    isOn: Binding(
                        get: { state.approveFollowers }, set: { send(EditProfileEvent.ApproveFollowersChanged(approve: $0)) })
                ) {
                    Text(.editProfileApproveFollowers)
                }
                .disabled(state.saving)
            } footer: {
                Text(.editProfileApproveFollowersHelp)
            }
            detailSection(.Location) {
                detailField(.editProfileLocality, model: locality, state: state.locality)
                detailField(.editProfileRegion, model: region, state: state.region)
                AppListRow(
                    Text(.editProfileCountry),
                    summary: countries.name(of: state.country).map { Text(verbatim: $0) } ?? Text(.editProfileNotSet),
                    opens: { choosingCountry = true })
            } header: {
                // The details' heading and help lead the first group's section, as they lead the shared form's groups.
                VStack(alignment: .leading, spacing: 6) {
                    Text(.editProfileDetailsHeading).font(.headline).foregroundStyle(.primary)
                    Text(.editProfileDetailsHelp).font(.subheadline.weight(.regular))
                    Text(LabelText.shared.detailGroup(group: .Location)).padding(.top, 10)
                }
                .textCase(nil)
            }
            detailSection(.BirthDate) { birthDate }
            detailSection(.Gender) { genderFields }
            detailSection(.Body) { bodyFields }
        }
        .disabled(!state.loaded)
    }

    /// The photo as it is now, picked from the library, changed or removed.
    private var photo: some View {
        HStack(spacing: 16) {
            ProfilePhotoView(avatar: state.avatar, name: displayName.text)
            VStack(alignment: .leading, spacing: 8) {
                PhotosPicker(selection: $photoItem, matching: .images, preferredItemEncoding: .compatible) {
                    Text(.editProfilePhotoChange)
                }
                if state.avatar != nil {
                    Button(.editProfilePhotoRemove, role: .destructive) { send(EditProfileEvent.RemoveAvatar.shared) }
                }
            }
            .buttonStyle(.borderless)
        }
    }

    /// The birthdate as stored, from a calendar of past days in a popover, and clearing it.
    private var birthDate: some View {
        HStack(spacing: 12) {
            Text(.editProfileBirthDate).frame(maxWidth: .infinity, alignment: .leading)
            if state.birthDay != nil {
                Button { pickingBirthDate = true } label: { Text(verbatim: state.birthDate) }
                    .accessibilityHint(Text(.editProfileBirthDateChoose))
                AppIconButton(.clear, label: .editProfileClear) { send(EditProfileEvent.BirthDateChanged(value: "")) }
                    .foregroundStyle(.secondary)
            } else {
                AppIconButton(.calendar, label: .editProfileBirthDateChoose) { pickingBirthDate = true }
            }
        }
        .buttonStyle(.borderless)
        .popover(isPresented: $pickingBirthDate) {
            DatePicker(
                selection: Binding(
                    get: { state.birthDay.map { CalendarDay($0).date(in: calendar) } ?? Date() },
                    set: { picked in
                        pickingBirthDate = false
                        send(EditProfileEvent.BirthDateChanged(date: CalendarDay(picked, in: calendar).localDate))
                    }),
                in: ...Date(), displayedComponents: .date
            ) {
                Text(.editProfileBirthDate)
            }
            .datePickerStyle(.graphical)
            .padding()
            .frame(minWidth: 320)
            .presentationCompactAdaptation(.popover)
        }
    }

    /// The gender's choice, and the athlete's own words when they describe it themselves.
    @ViewBuilder private var genderFields: some View {
        let choices = GenderChoices.shared
        // The state's own text, not the field's model: the field shows only once Self-describe is chosen, and a model whose field
        // isn't on screen never follows its state, so it would miss the saved words the presenter seeds after loading. The model is
        // read too, so typing redraws the choice.
        let _ = gender.text
        let words = state.gender.value
        AppPickerRow(
            String(localized: .editProfileGender),
            selection: Binding(
                get: { choices.chosen(gender: words, chose: choseSelfDescribe) },
                set: { choice in
                    choseSelfDescribe = choice == choices.SELF_DESCRIBE
                    send(EditProfileEvent.GenderChanged(value: choices.valueFor(choice: choice, gender: state.gender.value)))
                }),
            options: choices.all,
            optionLabel: { AppStrings.string(choices.label(choice: $0)) })
        if choices.selfDescribes(gender: words, chose: choseSelfDescribe) {
            AppTextField(
                label: .editProfileGenderDescribe, model: gender, state: state.gender, layout: .multiline,
                isError: !state.detailValid(text: words))
        }
    }

    /// Height in cm, or feet and inches as two rows, and weight, in the athlete's units.
    @ViewBuilder private var bodyFields: some View {
        if state.heightInFeetAndInches {
            AppTextField(
                label: state.heightLabel, model: height, state: state.height, layout: .row,
                options: TextFieldOptions(keyboard: .numberPad), isError: !state.heightValid)
            AppTextField(
                label: EditProfileTexts.shared.inchesRow, model: heightInches, state: state.heightInches, layout: .row,
                options: TextFieldOptions(keyboard: .decimalPad), isError: !state.heightValid)
        } else {
            AppTextField(
                label: state.heightLabel, model: height, state: state.height, layout: .row,
                options: TextFieldOptions(keyboard: .decimalPad), isError: !state.heightValid)
        }
        AppTextField(
            label: state.weightLabel, model: weight, state: state.weight, layout: .row,
            options: TextFieldOptions(keyboard: .decimalPad), isError: !state.weightValid)
    }

    /// A city or a state or province, in the error colour once longer than the detail limit.
    private func detailField(_ label: LocalizedStringResource, model: TextFieldModel, state field: KotlinTextFieldState) -> some View {
        AppTextField(
            label: label, model: model, state: field, layout: .row, options: TextFieldOptions(capitalization: .words),
            isError: !state.detailValid(text: model.text))
    }

    /// One detail group's section: its fields, then who may see all of them, with the Followers note and the now-hidden warning under it.
    private func detailSection<Fields: View, Header: View>(
        _ group: DetailGroup, @ViewBuilder fields: () -> Fields, @ViewBuilder header: () -> Header
    ) -> some View {
        Section {
            fields()
            AppPickerRow(
                name: String(localized: .visibilityLabel),
                selection: Binding(
                    get: { state.visibilityOf(group: group) },
                    set: { send(EditProfileEvent.VisibilityChanged(group: group, visibility: $0)) }),
                options: state.visibilityOptions.elements(as: String.self),
                optionLabel: { AppStrings.string(LabelText.shared.visibility(visibility: $0)) }
            ) {
                Text(LabelText.shared.visibilityScope(group: group))
            }
        } header: {
            header()
        } footer: {
            detailFooter(group)
        }
    }

    private func detailSection<Fields: View>(_ group: DetailGroup, @ViewBuilder fields: () -> Fields) -> some View {
        detailSection(group, fields: fields) { Text(LabelText.shared.detailGroup(group: group)) }
    }

    @ViewBuilder private func detailFooter(_ group: DetailGroup) -> some View {
        let note = state.followersNote(group: group)
        let hidden = state.isNowHidden(group: group)
        if note != nil || hidden {
            VStack(alignment: .leading, spacing: 8) {
                if let note { Text(note) }
                if hidden { Text(EditProfileTexts.shared.nowHidden(group: group)).foregroundStyle(.orange) }
            }
        }
    }

    private func send(_ event: any EditProfileEvent) {
        state.eventSink(event)
    }
}

/// The profile photo at 72 points, round: one picked and not yet saved from its bytes, the saved one from its URL, or the initial.
struct ProfilePhotoView: View {
    let avatar: (any AvatarImage)?
    let name: String

    var body: some View {
        ZStack {
            Avatar(url: (avatar as? AvatarImage.Saved)?.url, name: name, size: 72)
            if let picked = avatar as? AvatarImage.Picked, let image = UIImage(data: picked.jpeg.data) {
                Image(uiImage: image).resizable().scaledToFill().frame(width: 72, height: 72).clipShape(Circle())
            }
        }
        .accessibilityHidden(true)
    }
}

/// Edit profile's country choice: every country in the device's language, searched by Kotlin's rule (`CountryNames`), with Not set
/// first and the chosen one checked. A tap chooses and closes; Cancel closes without choosing.
struct CountrySheet: View {
    let countries: CountryNames
    let chosen: String
    let choose: (String) -> Void
    @State private var search = ""
    @Environment(\.dismiss) private var dismiss

    init(countries: CountryNames, chosen: String, search: String = "", choose: @escaping (String) -> Void) {
        self.countries = countries
        self.chosen = chosen
        self.choose = choose
        _search = State(initialValue: search)
    }

    var body: some View {
        NavigationStack {
            List {
                row(code: "", name: String(localized: .editProfileNotSet))
                ForEach(countries.matches(search), id: \.code) { country in
                    row(code: country.code, name: country.name)
                }
            }
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always))
            .navigationTitle(Text(.editProfileCountry))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(.actionCancel) { dismiss() } }
            }
        }
    }

    private func row(code: String, name: String) -> some View {
        Button {
            choose(code)
            dismiss()
        } label: {
            HStack {
                Text(verbatim: name).foregroundStyle(.primary)
                Spacer()
                if code == chosen {
                    Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(code == chosen ? .isSelected : [])
    }
}

#if DEBUG
    #Preview("Edit profile") {
        SamplePages(Samples.editProfile) { state in NavigationStack { EditProfileView(state: state) } }
    }

    #Preview("Edit profile, beyond loading") {
        SamplePages(Samples.editProfileVariants) { state in NavigationStack { EditProfileView(state: state) } }
    }

    #Preview("Country sheet") {
        CountrySheet(countries: CountryNames(), chosen: "US") { _ in }
    }
#endif
