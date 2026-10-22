// MY_PET 윈도우 실행기 (MyPet.exe 하나만 받으면 되게).
//
// 이 exe 안에 비트 프로그램(MyPet-windows-x64.zip: 실행 파일 + app + runtime)이 통째로 들어 있다.
// - 처음 실행: [설치] 를 누르면 안에 든 프로그램을 %LOCALAPPDATA%\MyPet 에 풀어서 설치하고 실행한다 (인터넷 필요 없음).
//   시작 메뉴에는 늘, 바탕 화면에는 "바탕 화면에 바로가기 추가" 를 골랐을 때 MY_PET 바로가기를 만든다.
// - 그다음부터: 설치된 %LOCALAPPDATA%\MyPet\MyPet.exe 를 바로 실행한다.
// - 이 exe 에 든 버전이 설치된 버전보다 새로우면 프로그램을 다시 풀어서 바꾼다 (settings.json 등은 그대로).
// 평소 앱 업데이트(MyPet.jar)는 설치된 앱이 스스로 한다.
//
// 자바 없이 돌아야 해서 윈도우에 기본으로 들어 있는 .NET Framework 4 로 만든다 (C# 5 문법만 사용).
// 빌드: scripts\build-launcher.ps1 (zip 을 리소스로 넣는다)
// 개발/시험용: MyPet.exe --install-only (설치만 하고 실행하지 않음)

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

[assembly: AssemblyTitle("MY_PET")]
[assembly: AssemblyProduct("MY_PET")]

static class MyPetLauncher
{
    const string PackageResource = "MyPet-windows-x64.zip";
    const string ReleasesUrl = "https://github.com/janghg0929/my_pet/releases/latest";

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
        string app = Path.Combine(home, "MyPet.exe");
        bool installOnly = Array.IndexOf(args, "--install-only") >= 0;

        Version installed = IsInstalled(home) ? ReadInstalledVersion(home) : null;
        Version packaged = ReadPackagedVersion();
        bool needInstall = installed == null || (packaged != null && packaged > installed);

        if (needInstall)
        {
            if (installed != null && IsAppRunning(app))
            {
                MessageBox.Show("새 버전으로 바꾸려면 비트를 먼저 꺼야 해요.\n"
                    + "비트를 클릭하고 Esc 를 누르거나, 우클릭 → 종료 후 다시 실행해 주세요.",
                    "MY_PET", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return 1;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            // 처음 설치할 때만 바로가기를 고르고 [설치] 를 누르게 한다 (새 버전으로 바꿀 때와 --install-only 는 바로)
            InstallForm form = new InstallForm(home, installed != null, installed == null && !installOnly);
            Application.Run(form);
            if (!form.Succeeded)
            {
                return 1;
            }
        }
        if (installOnly)
        {
            return 0;
        }

        try
        {
            ProcessStartInfo start = new ProcessStartInfo(app, JoinArgs(args));
            start.WorkingDirectory = home;
            start.UseShellExecute = false;
            Process.Start(start);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("비트를 실행하지 못했어요.\n\n" + e.Message, "MY_PET", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

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
            throw new FileNotFoundException("이 실행 파일 안에 비트 프로그램이 들어 있지 않아요.");
        }
        return stream;
    }

    /** 설치된 비트가 지금 켜져 있는지 (켜져 있으면 파일이 잠겨서 바꿀 수 없다). */
    static bool IsAppRunning(string app)
    {
        string target = Path.GetFullPath(app);
        int self = Process.GetCurrentProcess().Id;
        foreach (Process process in Process.GetProcessesByName("MyPet"))
        {
            try
            {
                if (process.Id != self
                    && string.Equals(Path.GetFullPath(process.MainModule.FileName), target, StringComparison.OrdinalIgnoreCase))
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

    static string JoinArgs(string[] args)
    {
        string[] quoted = new string[args.Length];
        for (int i = 0; i < args.Length; i++)
        {
            quoted[i] = "\"" + args[i].Replace("\"", "\\\"") + "\"";
        }
        return string.Join(" ", quoted);
    }

    /** 바로가기(.lnk) 이름: 시작 메뉴·바탕 화면 모두 "MY_PET" (Java 의 Shortcuts.NAME 과 같다). */
    const string ShortcutName = "MY_PET.lnk";

    /** WScript.Shell 로 바로가기를 만든다 (설치된 MyPet.exe 를 실행, 아이콘도 그 exe). 실패하면 false. */
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

    /** 설치 창: 처음 설치면 바로가기를 고르고 [설치], 새 버전이면 바로 진행. exe 안의 zip 을 풀고 닫힌다. */
    sealed class InstallForm : Form
    {
        readonly string home;
        readonly Label message;
        readonly ProgressBar bar;
        readonly CheckBox desktopShortcut;
        readonly Button install;
        readonly bool ask;
        public bool Succeeded;

        public InstallForm(string home, bool upgrade, bool ask)
        {
            this.home = home;
            this.ask = ask;
            Text = "MY_PET 설치";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(380, ask ? 150 : 110);
            BackColor = Color.FromArgb(0xFB, 0xF6, 0xEC);
            Font = new Font("Malgun Gothic", 9.5f);
            TopMost = true;
            try
            {
                Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); // exe 아이콘 (떨어지는 비트)
            }
            catch (Exception)
            {
                // 기본 아이콘
            }

            message = new Label();
            message.Text = ask ? "비트를 설치할게요. (한 번만 해요)"
                : (upgrade ? "비트를 새 버전으로 바꾸는 중이에요..." : "비트를 처음 설치하는 중이에요... (한 번만 해요)");
            message.SetBounds(18, 18, 344, 22);
            Controls.Add(message);

            bar = new ProgressBar();
            bar.SetBounds(18, 50, 344, 20);
            bar.Minimum = 0;
            bar.Maximum = 100;
            bar.Visible = !ask;
            Controls.Add(bar);

            // 처음 설치: 바탕 화면 바로가기를 고른다 (시작 메뉴에는 늘 만든다)
            desktopShortcut = new CheckBox();
            desktopShortcut.Text = "바탕 화면에 바로가기 추가";
            desktopShortcut.Checked = true;
            desktopShortcut.SetBounds(18, 46, 344, 24);
            desktopShortcut.Visible = ask;
            Controls.Add(desktopShortcut);

            Label where = new Label();
            where.Text = "설치 위치: " + home;
            where.ForeColor = Color.FromArgb(0x7D, 0x74, 0x68);
            where.Font = new Font("Malgun Gothic", 8f);
            where.SetBounds(18, 80, 344, 20);
            Controls.Add(where);

            install = new Button();
            install.Text = "설치";
            install.SetBounds(272, 108, 90, 30);
            install.BackColor = Color.FromArgb(0x4F, 0xC3, 0xF7);
            install.FlatStyle = FlatStyle.Flat;
            install.Visible = ask;
            install.Click += delegate { StartInstall(); };
            Controls.Add(install);
            if (ask)
            {
                AcceptButton = install;
            }
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            if (!ask)
            {
                StartInstall();
            }
        }

        void StartInstall()
        {
            install.Enabled = false;
            desktopShortcut.Enabled = false;
            desktopShortcut.Visible = false;
            bar.Visible = true;
            message.Text = "비트를 설치하는 중이에요...";
            Thread worker = new Thread(Run);
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

        void Run()
        {
            try
            {
                RemoveOldProgram(home);
                Extract(home);
                if (!IsInstalled(home))
                {
                    throw new InvalidDataException("설치한 폴더에 비트 프로그램이 없어요.");
                }
                if (ask)
                {
                    // 바로가기는 못 만들어도 설치는 된 것 (비트는 그대로 켠다)
                    CreateShortcut(Environment.GetFolderPath(Environment.SpecialFolder.Programs), home);
                    if (desktopShortcut.Checked)
                    {
                        CreateShortcut(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), home);
                    }
                }
                Succeeded = true;
                BeginInvoke((MethodInvoker)Close);
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

        /**
         * 새 버전으로 바꿀 때 예전 프로그램 파일(실행 파일, runtime, app 의 기본 파일)만 지운다.
         * settings.json, 자동 업데이트 jar(app\MyPet-x.y.z.jar), 잠금 파일은 남긴다.
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

        /** exe 안의 zip 에서 "MyPet/..." 를 home 아래로 푼다 (맨 앞 폴더 이름은 뺀다). */
        void Extract(string home)
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
                    if (done % 20 == 0)
                    {
                        SetProgress(done * 100 / Math.Max(1, total));
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
                SetProgress(100);
            }
        }
    }
}
