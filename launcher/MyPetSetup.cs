// MY_PET 윈도우 설치 프로그램 (MyPet-Setup.exe).
//
// 이 exe 안에 비트 프로그램(MyPet-windows-x64.zip: 실행 파일 + app + runtime)이 통째로 들어 있다.
// 받아서 한 번 실행하면 %LOCALAPPDATA%\MyPet 에 설치하고 (인터넷 필요 없음), 시작 메뉴와
// (고르면) 바탕 화면에 MY_PET 바로가기를 만든다. 그다음부터는 바로가기로 켠다. 이 exe 는 지워도 된다.
// - 설치 안 됨: [설치]
// - 이 exe 에 든 버전이 더 새로움: [업데이트] (settings.json, 꾸민 모습 등은 그대로)
// - 이미 같은(또는 더 새) 버전: [다시 설치] / [비트 켜기]
// 평소 앱 업데이트(MyPet.jar)는 설치된 앱이 스스로 한다.
//
// 바로가기와 이 프로그램은 비트를 켤 때 --shortcut 을 붙인다. 예전 MyPet.exe(받은 파일이 곧 실행기였던 2.1.2 까지)는
// 이걸 붙이지 않아서, 앱(LegacyLauncherNotice)이 "설치 파일을 다시 받아 주세요" 공지를 띄운다.
//
// 자바 없이 돌아야 해서 윈도우에 기본으로 들어 있는 .NET Framework 4 로 만든다 (C# 5 문법만 사용).
// 빌드: scripts\build-launcher.ps1 (zip 을 리소스로 넣는다)
// 개발/시험용: MyPet-Setup.exe --install-only (창 없이 설치만, 바로가기·실행 없음)

using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("MY_PET 설치")]
[assembly: AssemblyProduct("MY_PET")]

static class MyPetSetup
{
    const string PackageResource = "MyPet-windows-x64.zip";
    const string ReleasesUrl = "https://github.com/janghg0929/my_pet/releases/latest";
    /** 바로가기·설치 프로그램이 비트를 켤 때 붙이는 표시 (Java 의 MyPet.SHORTCUT_ARG 와 같다). */
    const string ShortcutArg = "--shortcut";
    /** 바로가기(.lnk) 이름: 시작 메뉴·바탕 화면 모두 "MY_PET" (Java 의 Shortcuts.NAME 과 같다). */
    const string ShortcutName = "MY_PET.lnk";

    static readonly Color Paper = Color.FromArgb(0xFB, 0xF6, 0xEC);
    static readonly Color Muted = Color.FromArgb(0x7D, 0x74, 0x68);
    static readonly Color Pet = Color.FromArgb(0x4F, 0xC3, 0xF7);

    /** 설치 폴더: Java 앱(Constants.appDataDir)과 같은 규칙 - %LOCALAPPDATA%\MyPet */
    static string HomeDirectory()
    {
        string local = Environment.GetEnvironmentVariable("LOCALAPPDATA");
        if (string.IsNullOrEmpty(local))
        {
            local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        }
        return Path.Combine(local, "MyPet");
    }

    [STAThread]
    static int Main(string[] args)
    {
        string home = HomeDirectory();
        Version installed = IsInstalled(home) ? ReadInstalledVersion(home) : null;
        Version packaged = ReadPackagedVersion();

        if (Array.IndexOf(args, "--install-only") >= 0)
        {
            // 시험용: 창 없이 설치만 (필요할 때만)
            if (installed == null || (packaged != null && packaged > installed))
            {
                RemoveOldProgram(home);
                Extract(home, null);
            }
            return IsInstalled(home) ? 0 : 1;
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new SetupForm(home, installed, packaged));
        return 0;
    }

    // ---------------------------------------------------------------- 설치 상태

    /** 설치된 앱(실행 파일 + 자바 런타임 + 앱 파일)이 모두 있는지. */
    static bool IsInstalled(string home)
    {
        return File.Exists(Path.Combine(home, "MyPet.exe"))
            && File.Exists(Path.Combine(home, "app", "MyPet.cfg"))
            && Directory.Exists(Path.Combine(home, "runtime"));
    }

    /** MyPet.cfg 의 "-Djpackage.app-version=2.0.1" 에서 버전을 읽는다. */
    static Version ParseCfgVersion(string cfg)
    {
        Match match = Regex.Match(cfg, @"jpackage\.app-version=([0-9][0-9.]*)");
        Version version;
        if (match.Success && Version.TryParse(match.Groups[1].Value.TrimEnd('.'), out version))
        {
            return version;
        }
        return null;
    }

    static Version ReadInstalledVersion(string home)
    {
        try
        {
            return ParseCfgVersion(File.ReadAllText(Path.Combine(home, "app", "MyPet.cfg")));
        }
        catch (Exception)
        {
            return null;
        }
    }

    /** 이 exe 안에 든 프로그램의 버전. */
    static Version ReadPackagedVersion()
    {
        try
        {
            using (Stream stream = OpenPackage())
            using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
            {
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    if (entry.FullName.Replace('\\', '/').EndsWith("/app/MyPet.cfg", StringComparison.OrdinalIgnoreCase))
                    {
                        using (StreamReader reader = new StreamReader(entry.Open()))
                        {
                            return ParseCfgVersion(reader.ReadToEnd());
                        }
                    }
                }
            }
        }
        catch (Exception)
        {
            // 버전을 모르면 null
        }
        return null;
    }

    static Stream OpenPackage()
    {
        Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(PackageResource);
        if (stream == null)
        {
            throw new FileNotFoundException("이 설치 파일 안에 비트 프로그램이 들어 있지 않아요.");
        }
        return stream;
    }

    /** 설치된 비트가 지금 켜져 있는지 (켜져 있으면 파일이 잠겨서 바꿀 수 없다). */
    static bool IsAppRunning(string home)
    {
        string target = Path.GetFullPath(Path.Combine(home, "MyPet.exe"));
        foreach (Process process in Process.GetProcessesByName("MyPet"))
        {
            try
            {
                if (string.Equals(Path.GetFullPath(process.MainModule.FileName), target, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }
            catch (Exception)
            {
                // 다른 사용자의 프로세스 등은 무시
            }
        }
        return false;
    }

    static string VersionText(Version version)
    {
        return version == null ? "" : "(v" + version + ")";
    }

    // ---------------------------------------------------------------- 설치

    /**
     * 새 버전으로 바꿀 때 예전 프로그램 파일(실행 파일, runtime, app 의 기본 파일)만 지운다.
     * settings.json, 자동 업데이트 jar(app\MyPet-x.y.z.jar), 꾸민 모습 사진(parts), 잠금 파일은 남긴다.
     */
    static void RemoveOldProgram(string home)
    {
        string runtime = Path.Combine(home, "runtime");
        if (Directory.Exists(runtime))
        {
            Directory.Delete(runtime, true);
        }
        DeleteIfExists(Path.Combine(home, "MyPet.exe"));
        DeleteIfExists(Path.Combine(home, "MyPet.ico"));
        DeleteIfExists(Path.Combine(home, "app", "MyPet.jar"));
        DeleteIfExists(Path.Combine(home, "app", "MyPet.cfg"));
        DeleteIfExists(Path.Combine(home, "app", ".jpackage.xml"));
    }

    static void DeleteIfExists(string path)
    {
        if (File.Exists(path))
        {
            File.Delete(path);
        }
    }

    /** exe 안의 zip 에서 "MyPet/..." 를 home 아래로 푼다 (맨 앞 폴더 이름은 뺀다). progress 는 0..100. */
    static void Extract(string home, Action<int> progress)
    {
        Directory.CreateDirectory(home);
        string root = Path.GetFullPath(home) + Path.DirectorySeparatorChar;
        using (Stream stream = OpenPackage())
        using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
        {
            int total = archive.Entries.Count;
            int done = 0;
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                done++;
                if (progress != null && done % 20 == 0)
                {
                    progress(done * 100 / Math.Max(1, total));
                }
                string name = entry.FullName.Replace('\\', '/');
                int slash = name.IndexOf('/');
                if (slash < 0)
                {
                    continue;
                }
                string relative = name.Substring(slash + 1);
                if (relative.Length == 0)
                {
                    continue;
                }
                string target = Path.GetFullPath(Path.Combine(home, relative.Replace('/', Path.DirectorySeparatorChar)));
                if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                {
                    continue; // 설치 폴더 밖으로 나가는 경로는 무시
                }
                if (name.EndsWith("/"))
                {
                    Directory.CreateDirectory(target);
                    continue;
                }
                Directory.CreateDirectory(Path.GetDirectoryName(target));
                entry.ExtractToFile(target, true);
            }
            if (progress != null)
            {
                progress(100);
            }
        }
    }

    // ---------------------------------------------------------------- 바로가기, 실행

    /** WScript.Shell 로 바로가기를 만든다 (설치된 MyPet.exe --shortcut, 아이콘도 그 exe). 실패하면 false. */
    static bool CreateShortcut(string folder, string home)
    {
        object shell = null;
        object link = null;
        try
        {
            if (string.IsNullOrEmpty(folder))
            {
                return false;
            }
            Directory.CreateDirectory(folder);
            string target = Path.Combine(home, "MyPet.exe");
            Type type = Type.GetTypeFromProgID("WScript.Shell");
            shell = Activator.CreateInstance(type);
            link = type.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell,
                new object[] { Path.Combine(folder, ShortcutName) });
            Type linkType = link.GetType();
            linkType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { target });
            linkType.InvokeMember("Arguments", BindingFlags.SetProperty, null, link, new object[] { ShortcutArg });
            linkType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link, new object[] { home });
            linkType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link, new object[] { target + ",0" });
            linkType.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { "데스크톱 펫 비트" });
            linkType.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
            return true;
        }
        catch (Exception)
        {
            return false;
        }
        finally
        {
            if (link != null) { Marshal.ReleaseComObject(link); }
            if (shell != null) { Marshal.ReleaseComObject(shell); }
        }
    }

    static string DesktopFolder()
    {
        return Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
    }

    static string StartMenuFolder()
    {
        return Environment.GetFolderPath(Environment.SpecialFolder.Programs);
    }

    /** 설치된 비트를 켠다 (바로가기처럼 --shortcut 을 붙여서). */
    static bool StartApp(string home)
    {
        try
        {
            ProcessStartInfo start = new ProcessStartInfo(Path.Combine(home, "MyPet.exe"), ShortcutArg);
            start.WorkingDirectory = home;
            start.UseShellExecute = false;
            Process.Start(start);
            return true;
        }
        catch (Exception e)
        {
            MessageBox.Show("비트를 켜지 못했어요.\n\n" + e.Message, "MY_PET", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return false;
        }
    }

    // ---------------------------------------------------------------- 설치 창


    /** 설치 창의 단계. */
    enum Stage { Fresh, Upgrade, Current, Installing, Done }

    /** 설치 창: 고르기 → 설치 중(진행 막대) → 끝 (어디서 켜는지 안내 + [비트 켜기]). */
    sealed class SetupForm : Form
    {
        readonly string home;
        readonly Label title;
        readonly Label detail;
        readonly CheckBox desktopShortcut;
        readonly ProgressBar bar;
        readonly Button primary;
        readonly Button secondary;
        Stage stage;

        public SetupForm(string home, Version installed, Version packaged)
        {
            this.home = home;
            bool upgrade = installed != null && packaged != null && packaged > installed;
            stage = installed == null ? Stage.Fresh : (upgrade ? Stage.Upgrade : Stage.Current);

            Text = "MY_PET 설치";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(400, 186);
            BackColor = Paper;
            Font = new Font("Malgun Gothic", 9.5f);
            try
            {
                Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); // exe 아이콘 (떨어지는 비트)
            }
            catch (Exception)
            {
                // 기본 아이콘
            }

            title = new Label();
            title.Font = new Font("Malgun Gothic", 12f, FontStyle.Bold);
            title.SetBounds(20, 16, 360, 26);
            Controls.Add(title);

            detail = new Label();
            detail.SetBounds(20, 46, 360, 40);
            Controls.Add(detail);

            desktopShortcut = new CheckBox();
            desktopShortcut.Text = "바탕 화면에 바로가기 추가";
            desktopShortcut.Checked = true;
            desktopShortcut.SetBounds(20, 90, 360, 24);
            Controls.Add(desktopShortcut);

            bar = new ProgressBar();
            bar.SetBounds(20, 92, 360, 20);
            bar.Minimum = 0;
            bar.Maximum = 100;
            bar.Visible = false;
            Controls.Add(bar);

            Label where = new Label();
            where.Text = "설치 위치: " + home;
            where.ForeColor = Muted;
            where.Font = new Font("Malgun Gothic", 8f);
            where.SetBounds(20, 118, 360, 18);
            Controls.Add(where);

            primary = MakeButton(282);
            primary.BackColor = Pet;
            primary.Click += delegate { OnPrimary(); };
            secondary = MakeButton(176);
            secondary.Click += delegate { OnSecondary(); };
            AcceptButton = primary;

            switch (stage)
            {
                case Stage.Current:
                    title.Text = "비트가 이미 설치되어 있어요 " + VersionText(installed);
                    detail.Text = "바로 켜거나, 문제가 있으면 다시 설치할 수 있어요.\n설정과 꾸민 모습은 그대로 남아요.";
                    primary.Text = "비트 켜기";
                    secondary.Text = "다시 설치";
                    break;
                case Stage.Upgrade:
                    title.Text = "새 버전으로 업데이트할게요 " + VersionText(packaged);
                    detail.Text = "지금 설치된 버전" + VersionText(installed) + "을 바꿔요.\n설정과 꾸민 모습은 그대로 남아요.";
                    primary.Text = "업데이트";
                    secondary.Visible = false;
                    break;
                default:
                    title.Text = "비트를 설치할게요 " + VersionText(packaged);
                    detail.Text = "바탕 화면 위를 돌아다니는 펫이에요. 한 번만 설치하면\n다음부터는 MY_PET 바로가기로 켜요.";
                    primary.Text = "설치";
                    secondary.Visible = false;
                    break;
            }
        }

        Button MakeButton(int x)
        {
            Button button = new Button();
            button.SetBounds(x, 144, 98, 30);
            button.FlatStyle = FlatStyle.Flat;
            button.BackColor = Color.White;
            Controls.Add(button);
            return button;
        }

        void OnPrimary()
        {
            switch (stage)
            {
                case Stage.Fresh:
                case Stage.Upgrade:
                    StartInstall();
                    break;
                case Stage.Current:
                    // 바로가기가 없어졌으면 다시 만든다 (시작 메뉴는 늘, 바탕 화면은 고르면)
                    CreateShortcut(StartMenuFolder(), home);
                    if (desktopShortcut.Checked && !File.Exists(Path.Combine(DesktopFolder(), ShortcutName)))
                    {
                        CreateShortcut(DesktopFolder(), home);
                    }
                    StartAndClose();
                    break;
                case Stage.Done:
                    StartAndClose();
                    break;
            }
        }

        void OnSecondary()
        {
            if (stage == Stage.Current)
            {
                StartInstall(); // 다시 설치
            }
            else if (stage == Stage.Done)
            {
                Close();
            }
        }

        void StartAndClose()
        {
            if (StartApp(home))
            {
                Close();
            }
        }

        void StartInstall()
        {
            if (IsAppRunning(home))
            {
                MessageBox.Show(this, "설치하려면 비트를 먼저 꺼야 해요.\n"
                    + "비트를 클릭하고 Esc 를 누르거나, 우클릭 → 종료한 다음 다시 눌러 주세요.",
                    "MY_PET 설치", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            stage = Stage.Installing;
            primary.Enabled = false;
            secondary.Enabled = false;
            desktopShortcut.Visible = false;
            bar.Visible = true;
            title.Text = "비트를 설치하는 중이에요...";
            detail.Text = "잠깐이면 돼요.";
            bool desktop = desktopShortcut.Checked;
            Thread worker = new Thread(delegate () { Run(desktop); });
            worker.IsBackground = true;
            worker.Start();
        }

        void SetProgress(int percent)
        {
            if (IsDisposed)
            {
                return;
            }
            BeginInvoke((MethodInvoker)delegate
            {
                bar.Value = Math.Max(0, Math.Min(100, percent));
            });
        }

        void Run(bool desktop)
        {
            try
            {
                RemoveOldProgram(home);
                Extract(home, SetProgress);
                if (!IsInstalled(home))
                {
                    throw new InvalidDataException("설치한 폴더에 비트 프로그램이 없어요.");
                }
                // 바로가기는 못 만들어도 설치는 된 것
                bool menu = CreateShortcut(StartMenuFolder(), home);
                bool onDesktop = desktop && CreateShortcut(DesktopFolder(), home);
                BeginInvoke((MethodInvoker)delegate { ShowDone(menu, onDesktop); });
            }
            catch (Exception e)
            {
                BeginInvoke((MethodInvoker)delegate
                {
                    DialogResult answer = MessageBox.Show(this,
                        "비트를 설치하지 못했어요.\n\n" + e.Message + "\n\n다운로드 페이지를 열까요?",
                        "MY_PET 설치", MessageBoxButtons.YesNo, MessageBoxIcon.Error);
                    if (answer == DialogResult.Yes)
                    {
                        try { Process.Start(ReleasesUrl); } catch (Exception) { }
                    }
                    Close();
                });
            }
        }

        /** 끝: 어디서 켜는지 알려 주고 [비트 켜기] / [닫기]. */
        void ShowDone(bool menu, bool onDesktop)
        {
            stage = Stage.Done;
            bar.Visible = false;
            desktopShortcut.Visible = false;
            title.Text = "설치가 끝났어요!";
            string from = onDesktop ? "바탕 화면·시작 메뉴의 MY_PET 바로가기로" : (menu ? "시작 메뉴의 MY_PET 바로가기로" : "설치 위치의 MyPet.exe로");
            detail.Text = "다음부터는 " + from + " 켜요.\n받은 설치 파일은 지워도 돼요.";
            primary.Text = "비트 켜기";
            primary.Enabled = true;
            secondary.Text = "닫기";
            secondary.Visible = true;
            secondary.Enabled = true;
            primary.Focus();
        }
    }
}
