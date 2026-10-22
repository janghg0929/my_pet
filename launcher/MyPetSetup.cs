// MY_PET 윈도우 설치 프로그램 (MyPet-Setup.exe).
//
// 이 exe 안에 비트 프로그램(MyPet-windows-x64.zip: 실행 파일 + app + runtime)이 통째로 들어 있다.
// 받아서 한 번 실행하면 %LOCALAPPDATA%\MyPet 에 설치하고 (인터넷 필요 없음), 시작 메뉴와
// (고르면) 바탕 화면에 MY_PET 바로가기를 만든다. 그다음부터는 바로가기로 켠다. 이 exe 는 지워도 된다.
// - 설치 안 됨: [설치]
// - 이 exe 에 든 버전이 더 새로움: [업데이트] (settings.json, 꾸민 모습 등은 그대로)
// - 이미 같은(또는 더 새) 버전: [다시 설치] / [비트 켜기]
// - 설치되어 있으면 [삭제]: 프로그램과 바로가기를 지운다 (설정·꾸민 모습은 고르면 휴지통으로)
// 평소 앱 업데이트(MyPet.jar)는 설치된 앱이 스스로 한다.
//
// 바로가기와 이 프로그램은 비트를 켤 때 --shortcut 을 붙인다. 예전 MyPet.exe(받은 파일이 곧 실행기였던 2.1.2 까지)는
// 이걸 붙이지 않아서, 앱(LegacyLauncherNotice)이 "설치 파일을 다시 받아 주세요" 공지를 띄운다.
//
// 자바 없이 돌아야 해서 윈도우에 기본으로 들어 있는 .NET Framework 4 로 만든다 (C# 5 문법만 사용).
// 빌드: scripts\build-launcher.ps1 (zip 을 리소스로 넣는다)
// 개발/시험용: MyPet-Setup.exe --install-only (창 없이 설치만, 바로가기·실행 없음)
//             MyPet-Setup.exe --uninstall-only [--remove-data] (창 없이 삭제만)

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
    /** 바로가기 아이콘: 설치 폴더에 꺼내 두는 파일 이름 (Java 의 Shortcuts.ICON_FILE 과 같다)과 이 exe 안의 리소스 이름. */
    const string IconFile = "MyPet-icon.ico";
    const string IconResource = "MyPet.ico";

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
        if (Array.IndexOf(args, "--uninstall-only") >= 0)
        {
            // 시험용: 창 없이 삭제만 (--remove-data 면 설정까지)
            Uninstall(home, Array.IndexOf(args, "--remove-data") >= 0);
            return IsInstalled(home) ? 1 : 0;
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
        // 설치 폴더의 비트뿐 아니라 다른 곳(예전 MyPet.exe, 미리보기 폴더)에서 켠 비트도 같은 설치 폴더의
        // my_pet.lock 과 settings.json 을 쓰고 있어서, 이름이 MyPet 인 프로세스가 하나라도 있으면 켜져 있는 것으로 본다
        Process[] processes = Process.GetProcessesByName("MyPet");
        foreach (Process process in processes)
        {
            process.Dispose();
        }
        return processes.Length > 0 || IsLockHeld(home);
    }

    /** 비트가 잡고 있는 단일 실행 잠금 파일(my_pet.lock)을 다른 프로그램이 쓰고 있는지. */
    static bool IsLockHeld(string home)
    {
        string path = Path.Combine(home, "my_pet.lock");
        if (!File.Exists(path))
        {
            return false;
        }
        try
        {
            using (FileStream stream = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            {
                return false;
            }
        }
        catch (IOException)
        {
            return true;
        }
        catch (Exception)
        {
            return false;
        }
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

    /**
     * 바로가기 아이콘을 설치 폴더에 꺼내 둔다 (이 exe 에 리소스로 들어 있다). 꺼낸 경로, 못 하면 exe.
     * exe 를 아이콘으로 쓰면 윈도우가 예전(자바) 아이콘을 같은 경로로 기억해 두고 계속 보여 줘서 따로 둔다.
     */
    static string WriteIcon(string home)
    {
        string icon = Path.Combine(home, IconFile);
        try
        {
            using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(IconResource))
            {
                if (stream == null)
                {
                    return Path.Combine(home, "MyPet.exe");
                }
                using (FileStream file = File.Create(icon))
                {
                    stream.CopyTo(file);
                }
            }
            return icon;
        }
        catch (Exception)
        {
            return File.Exists(icon) ? icon : Path.Combine(home, "MyPet.exe");
        }
    }

    [DllImport("shell32.dll")]
    static extern void SHChangeNotify(int eventId, int flags, IntPtr item1, IntPtr item2);

    /** 탐색기에 아이콘이 바뀌었다고 알린다 (바탕 화면 아이콘을 다시 그리게). */
    static void RefreshIcons()
    {
        try
        {
            SHChangeNotify(0x08000000, 0, IntPtr.Zero, IntPtr.Zero); // SHCNE_ASSOCCHANGED, SHCNF_IDLIST
        }
        catch (Exception)
        {
            // 못 알려도 다음에 다시 그릴 때 바뀐다
        }
    }

    /** WScript.Shell 로 바로가기를 만든다 (설치된 MyPet.exe --shortcut, 아이콘은 MyPet-icon.ico). 실패하면 false. */
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
            linkType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link, new object[] { WriteIcon(home) + ",0" });
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

    // ---------------------------------------------------------------- 삭제

    /** 바로가기가 이 설치본을 가리키는지 (다른 프로그램의 같은 이름 바로가기는 지우지 않게). */
    static bool PointsToInstall(string link, string home)
    {
        object shell = null;
        object shortcut = null;
        try
        {
            Type type = Type.GetTypeFromProgID("WScript.Shell");
            shell = Activator.CreateInstance(type);
            shortcut = type.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { link });
            string target = (string)shortcut.GetType().InvokeMember("TargetPath", BindingFlags.GetProperty, null, shortcut, null);
            return string.Equals(Path.GetFullPath(target), Path.GetFullPath(Path.Combine(home, "MyPet.exe")), StringComparison.OrdinalIgnoreCase);
        }
        catch (Exception)
        {
            return false;
        }
        finally
        {
            if (shortcut != null) { Marshal.ReleaseComObject(shortcut); }
            if (shell != null) { Marshal.ReleaseComObject(shell); }
        }
    }

    /**
     * 비트를 지운다: 바탕 화면·시작 메뉴 바로가기와 프로그램 파일.
     * removeData 가 아니면 settings.json 과 꾸민 모습 사진(parts)은 남겨서, 다시 설치하면 그대로 돌아온다.
     */
    static void Uninstall(string home, bool removeData)
    {
        foreach (string folder in new string[] { DesktopFolder(), StartMenuFolder() })
        {
            if (string.IsNullOrEmpty(folder))
            {
                continue;
            }
            string link = Path.Combine(folder, ShortcutName);
            if (File.Exists(link) && PointsToInstall(link, home))
            {
                File.Delete(link);
            }
        }
        // 프로그램 파일 (설정은 건드리지 않는다)
        RemoveOldProgram(home);
        string app = Path.Combine(home, "app");
        if (Directory.Exists(app))
        {
            Directory.Delete(app, true); // 자동 업데이트로 받은 jar 도
        }
        // 작은 파일은 못 지워도(탐색기가 아이콘을 잡고 있는 등) 그냥 남겨 둔다
        foreach (string name in new string[] { IconFile, "my_pet.lock", "shortcut-icons", "start-menu-shortcut", "shortcuts-created" })
        {
            try
            {
                DeleteIfExists(Path.Combine(home, name));
            }
            catch (Exception)
            {
                // 남아도 괜찮다
            }
        }
        if (removeData)
        {
            // 설정과 꾸민 모습은 휴지통으로 (실수로 지워도 되살릴 수 있게)
            foreach (string name in new string[] { "settings.json", "settings.json.broken", "settings.json.tmp" })
            {
                string file = Path.Combine(home, name);
                if (File.Exists(file))
                {
                    Microsoft.VisualBasic.FileIO.FileSystem.DeleteFile(file,
                        Microsoft.VisualBasic.FileIO.UIOption.OnlyErrorDialogs, Microsoft.VisualBasic.FileIO.RecycleOption.SendToRecycleBin);
                }
            }
            string parts = Path.Combine(home, "parts");
            if (Directory.Exists(parts))
            {
                Microsoft.VisualBasic.FileIO.FileSystem.DeleteDirectory(parts,
                    Microsoft.VisualBasic.FileIO.UIOption.OnlyErrorDialogs, Microsoft.VisualBasic.FileIO.RecycleOption.SendToRecycleBin);
            }
            try
            {
                if (Directory.Exists(home) && Directory.GetFileSystemEntries(home).Length == 0)
                {
                    Directory.Delete(home);
                }
            }
            catch (Exception)
            {
                // 빈 폴더가 남아도 괜찮다
            }
        }
        RefreshIcons();
    }

    // ---------------------------------------------------------------- 설치 창


    /** 설치 창의 단계. */
    enum Stage { Fresh, Upgrade, Current, Installing, Done, ConfirmRemove, Removing, Removed }

    /**
     * 설치 창: 고르기 → 설치 중(진행 막대) → 끝 (어디서 켜는지 안내 + [비트 켜기]).
     * 이미 설치되어 있으면 [삭제] 도 있다: 삭제할까요? (설정도 지울지 고르기) → 지우는 중 → 지웠어요.
     */
    sealed class SetupForm : Form
    {
        readonly string home;
        readonly Version installed;
        readonly Version packaged;
        readonly Label title;
        readonly Label detail;
        readonly CheckBox desktopShortcut;
        readonly CheckBox removeData;
        readonly ProgressBar bar;
        readonly Button primary;
        readonly Button secondary;
        readonly Button remove;
        Stage stage;
        /** 삭제를 취소하면 돌아갈 단계. */
        Stage choice;

        public SetupForm(string home, Version installed, Version packaged)
        {
            this.home = home;
            this.installed = installed;
            this.packaged = packaged;
            bool upgrade = installed != null && packaged != null && packaged > installed;
            choice = installed == null ? Stage.Fresh : (upgrade ? Stage.Upgrade : Stage.Current);

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

            removeData = new CheckBox();
            removeData.Text = "설정과 꾸민 모습도 지우기 (휴지통으로)";
            removeData.SetBounds(20, 90, 360, 24);
            removeData.Visible = false;
            Controls.Add(removeData);

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
            // 이미 설치되어 있을 때만: 왼쪽 아래 [삭제]
            remove = MakeButton(20);
            remove.Text = "삭제";
            remove.ForeColor = Color.FromArgb(0xC8, 0x40, 0x2F);
            remove.Click += delegate { ShowConfirmRemove(); };
            AcceptButton = primary;
            ShowChoice();
        }

        /** 처음 화면: 설치 / 업데이트 / 이미 설치됨. */
        void ShowChoice()
        {
            stage = choice;
            removeData.Visible = false;
            desktopShortcut.Visible = true;
            primary.Enabled = true;
            secondary.Enabled = true;
            remove.Visible = installed != null;
            switch (stage)
            {
                case Stage.Current:
                    title.Text = "비트가 이미 설치되어 있어요 " + VersionText(installed);
                    detail.Text = "바로 켜거나, 문제가 있으면 다시 설치할 수 있어요.\n설정과 꾸민 모습은 그대로 남아요.";
                    primary.Text = "비트 켜기";
                    secondary.Text = "다시 설치";
                    secondary.Visible = true;
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

        /** 삭제할까요? 설정도 지울지 고른다 (기본은 남김: 다시 설치하면 그대로). */
        void ShowConfirmRemove()
        {
            stage = Stage.ConfirmRemove;
            title.Text = "비트를 삭제할까요?";
            detail.Text = "프로그램과 MY_PET 바로가기를 지워요.\n설정은 남겨 두면 다시 설치할 때 그대로예요.";
            desktopShortcut.Visible = false;
            removeData.Checked = false;
            removeData.Visible = true;
            remove.Visible = false;
            primary.Text = "삭제";
            secondary.Text = "취소";
            secondary.Visible = true;
            secondary.Focus();
        }

        void StartRemove()
        {
            if (IsAppRunning(home))
            {
                MessageBox.Show(this, "삭제하려면 비트를 먼저 꺼야 해요.\n"
                    + "비트를 클릭하고 Esc 를 누르거나, 우클릭 → 종료한 다음 다시 눌러 주세요.",
                    "MY_PET 삭제", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            bool data = removeData.Checked;
            if (data && MessageBox.Show(this, "몸 색, 만든 부위, 꾸민 모습, 저장한 모습이 모두 지워져요.\n"
                + "(휴지통으로 옮겨서, 실수였다면 휴지통에서 되살릴 수 있어요)\n\n정말 지울까요?",
                "MY_PET 삭제", MessageBoxButtons.YesNo, MessageBoxIcon.Warning, MessageBoxDefaultButton.Button2) != DialogResult.Yes)
            {
                return;
            }
            stage = Stage.Removing;
            removeData.Visible = false;
            primary.Enabled = false;
            secondary.Enabled = false;
            bar.Style = ProgressBarStyle.Marquee;
            bar.Visible = true;
            title.Text = "비트를 지우는 중이에요...";
            detail.Text = "잠깐이면 돼요.";
            Thread worker = new Thread(delegate ()
            {
                try
                {
                    Uninstall(home, data);
                    BeginInvoke((MethodInvoker)delegate { ShowRemoved(data); });
                }
                catch (Exception e)
                {
                    BeginInvoke((MethodInvoker)delegate
                    {
                        MessageBox.Show(this, "비트를 다 지우지 못했어요.\n\n" + e.Message + "\n\n비트가 꺼져 있는지 확인하고 다시 해 주세요.",
                            "MY_PET 삭제", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        Close();
                    });
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }

        void ShowRemoved(bool data)
        {
            stage = Stage.Removed;
            bar.Visible = false;
            desktopShortcut.Visible = false;
            removeData.Visible = false;
            remove.Visible = false;
            title.Text = "비트를 삭제했어요";
            detail.Text = data ? "설정과 꾸민 모습은 휴지통으로 옮겼어요.\n그동안 함께해 줘서 고마워요!"
                : "설정과 꾸민 모습은 남겨 뒀어요.\n다시 설치하면 그대로 돌아와요.";
            primary.Text = "닫기";
            primary.Enabled = true;
            secondary.Visible = false;
            primary.Focus();
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
                    if (desktopShortcut.Checked || File.Exists(Path.Combine(DesktopFolder(), ShortcutName)))
                    {
                        CreateShortcut(DesktopFolder(), home); // 있던 것도 아이콘·표시를 고쳐 쓴다
                    }
                    RefreshIcons();
                    StartAndClose();
                    break;
                case Stage.Done:
                    StartAndClose();
                    break;
                case Stage.ConfirmRemove:
                    StartRemove();
                    break;
                case Stage.Removed:
                    Close();
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
            else if (stage == Stage.ConfirmRemove)
            {
                ShowChoice(); // 취소
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
            remove.Visible = false;
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
                RefreshIcons();
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
